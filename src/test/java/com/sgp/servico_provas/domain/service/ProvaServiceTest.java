/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sgp.servico_provas.api.dto.request.ProvaRequest;
import com.sgp.servico_provas.api.dto.response.ProvaResponse;
import com.sgp.servico_provas.config.mapper.ModelMapperConfig;
import com.sgp.servico_provas.domain.entity.Prova;
import com.sgp.servico_provas.domain.entity.ProvaQuestao;
import com.sgp.servico_provas.domain.enums.Status;
import com.sgp.servico_provas.domain.exception.AcessoNegadoException;
import com.sgp.servico_provas.domain.exception.RecursoNaoEncontradoException;
import com.sgp.servico_provas.domain.exception.RegraNegocioException;
import com.sgp.servico_provas.domain.exception.ServicoExternoException;
import com.sgp.servico_provas.domain.repository.ProvaRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 *
 * @author diego-casemiro
 */
@ExtendWith(MockitoExtension.class)
class ProvaServiceTest {

    private static final UUID AUTOR = UUID.randomUUID();
    private static final UUID OUTRO_USUARIO = UUID.randomUUID();
    private static final String TOKEN = "token-do-solicitante";

    @Mock
    private ProvaRepository provaRepository;

    private ProvaService provaService;

    // Simula o servico-usuarios e o servico-questoes: guarda as requisições recebidas e responde conforme configurado
    private HttpServer servicosExternos;
    private final List<String> caminhosRecebidos = new ArrayList<>();
    private final List<String> authorizationsRecebidos = new ArrayList<>();
    private int statusMateria = 200;
    private int statusQuestao = 200;
    // id da questão -> id da matéria dela; questão fora do mapa responde 404
    private final Map<Long, Long> materiaPorQuestao = new HashMap<>(Map.of(10L, 1L, 11L, 1L, 20L, 2L));

    @BeforeEach
    void setUp() throws IOException {
        servicosExternos = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servicosExternos.createContext("/api/v1/materias/", exchange ->
                responder(exchange, statusMateria, "{\"id\":1,\"nome\":\"Cálculo\"}"));
        servicosExternos.createContext("/api/v1/questoes/", exchange -> {
            String caminho = exchange.getRequestURI().getPath();
            Long questaoId = Long.valueOf(caminho.substring(caminho.lastIndexOf('/') + 1));
            Long materiaId = materiaPorQuestao.get(questaoId);
            if (statusQuestao == 200 && materiaId == null) {
                responder(exchange, 404, "{}");
            } else {
                responder(exchange, statusQuestao, "{\"id\":" + questaoId + ",\"materiaId\":" + materiaId + "}");
            }
        });
        servicosExternos.start();

        String url = "http://localhost:" + servicosExternos.getAddress().getPort() + "/api";
        provaService = new ProvaService(provaRepository, new ModelMapperConfig().modelMapper());
        ReflectionTestUtils.setField(provaService, "servicoUsuariosUrl", url);
        ReflectionTestUtils.setField(provaService, "servicoQuestoesUrl", url);

        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "none")
                .subject(AUTOR.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        servicosExternos.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void listarDeveMapearAsProvasEPassarAPaginacaoParaORepository() {
        Pageable pageable = PageRequest.of(2, 10, Sort.by("titulo"));
        when(provaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(prova(1L, AUTOR, Status.RASCUNHO, 10L, 11L))));

        Page<ProvaResponse> resultado = provaService.listar(1L, Status.RASCUNHO, "cálculo", pageable, AUTOR, false);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(resultado.getContent().get(0).getQuestoes()).extracting("questaoId").containsExactly(10L, 11L);
        verify(provaRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listarDeveRecusarOrdenacaoPorCampoInexistente() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("senha"));

        assertThatThrownBy(() -> provaService.listar(null, null, null, pageable, AUTOR, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("ordenacao.campo-invalido");
        verify(provaRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void buscarPorIdDeveRetornarAProvaParaOAutor() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));

        ProvaResponse resultado = provaService.buscarPorId(1L, AUTOR, false);

        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getUsuarioKeycloakId()).isEqualTo(AUTOR);
        assertThat(resultado.getTitulo()).isEqualTo("Prova de Cálculo I");
    }

    @Test
    void buscarPorIdDevePermitirQueOAdminVejaProvaDeOutroUsuario() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));

        ProvaResponse resultado = provaService.buscarPorId(1L, OUTRO_USUARIO, true);

        assertThat(resultado.getId()).isEqualTo(1L);
    }

    @Test
    void buscarPorIdDeveNegarAcessoAQuemNaoEOAutor() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));

        assertThatThrownBy(() -> provaService.buscarPorId(1L, OUTRO_USUARIO, false))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("prova.somente-autor");
    }

    @Test
    void buscarPorIdDeveLancarNaoEncontradoQuandoAProvaNaoExiste() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provaService.buscarPorId(99L, AUTOR, false))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("prova.nao-encontrada");
    }

    @Test
    void criarDeveGravarRascunhoComOAutorDoTokenEValidarMateriaEQuestoes() {
        when(provaRepository.save(any(Prova.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ProvaResponse resultado = provaService.criar(request(1L, 10L, 11L), AUTOR);

        ArgumentCaptor<Prova> salva = ArgumentCaptor.forClass(Prova.class);
        verify(provaRepository).save(salva.capture());
        Prova prova = salva.getValue();
        assertThat(prova.getUsuarioKeycloakId()).isEqualTo(AUTOR);
        assertThat(prova.getStatus()).isEqualTo(Status.RASCUNHO);
        assertThat(prova.getMateriaId()).isEqualTo(1L);
        assertThat(prova.getQuestoes()).hasSize(2)
                .allSatisfy(provaQuestao -> assertThat(provaQuestao.getProva()).isSameAs(prova));
        assertThat(resultado.getQuestoes()).extracting("questaoId").containsExactly(10L, 11L);

        // Consulta a matéria e cada questão repassando o token de quem fez a requisição
        assertThat(caminhosRecebidos)
                .containsExactly("/api/v1/materias/1", "/api/v1/questoes/10", "/api/v1/questoes/11");
        assertThat(authorizationsRecebidos).containsOnly("Bearer " + TOKEN);
    }

    @Test
    void criarDevePermitirRascunhoSemQuestoes() {
        when(provaRepository.save(any(Prova.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ProvaResponse resultado = provaService.criar(request(1L), AUTOR);

        assertThat(resultado.getQuestoes()).isEmpty();
        assertThat(caminhosRecebidos).containsExactly("/api/v1/materias/1");
    }

    @Test
    void criarDeveRecusarQuestaoRepetida() {
        assertThatThrownBy(() -> provaService.criar(request(1L, 10L, 10L), AUTOR))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("prova.questao-repetida");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void criarDeveRecusarQuestaoDeOutraMateria() {
        assertThatThrownBy(() -> provaService.criar(request(1L, 10L, 20L), AUTOR))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("prova.questao-de-outra-materia");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarNaoEncontradoQuandoAQuestaoNaoExisteNoServicoQuestoes() {
        assertThatThrownBy(() -> provaService.criar(request(1L, 99L), AUTOR))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("questao.nao-encontrada");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void criarDeveNegarAcessoQuandoOServicoQuestoesResponde403() {
        statusQuestao = 403;

        assertThatThrownBy(() -> provaService.criar(request(1L, 10L), AUTOR))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("questao.sem-acesso");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarServicoExternoQuandoOServicoQuestoesFalha() {
        statusQuestao = 500;

        assertThatThrownBy(() -> provaService.criar(request(1L, 10L), AUTOR))
                .isInstanceOf(ServicoExternoException.class)
                .hasMessage("servico-questoes.indisponivel");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarNaoEncontradoQuandoAMateriaNaoExisteNoServicoUsuarios() {
        statusMateria = 404;

        assertThatThrownBy(() -> provaService.criar(request(99L, 10L), AUTOR))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("materia.nao-encontrada");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarServicoExternoQuandoOsServicosEstaoFora() {
        servicosExternos.stop(0);

        assertThatThrownBy(() -> provaService.criar(request(1L, 10L), AUTOR))
                .isInstanceOf(ServicoExternoException.class)
                .hasMessage("servico-usuarios.indisponivel");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void atualizarNaoDeveConsultarOServicoUsuariosQuandoAMateriaNaoMudou() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));
        when(provaRepository.save(any(Prova.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ProvaResponse resultado = provaService.atualizar(1L, request(1L, 11L), AUTOR, false);

        assertThat(caminhosRecebidos).containsExactly("/api/v1/questoes/11");
        // As questões antigas são trocadas pelas do request
        assertThat(resultado.getQuestoes()).extracting("questaoId").containsExactly(11L);
    }

    @Test
    void atualizarDeveValidarANovaMateriaQuandoElaMudou() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));
        when(provaRepository.save(any(Prova.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ProvaResponse resultado = provaService.atualizar(1L, request(2L, 20L), AUTOR, false);

        assertThat(caminhosRecebidos).containsExactly("/api/v1/materias/2", "/api/v1/questoes/20");
        assertThat(resultado.getMateriaId()).isEqualTo(2L);
    }

    @Test
    void atualizarDeveRecusarProvaPublicada() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.PUBLICADA, 10L)));

        assertThatThrownBy(() -> provaService.atualizar(1L, request(1L, 11L), AUTOR, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("prova.somente-rascunho");
        assertThat(caminhosRecebidos).isEmpty();
        verify(provaRepository, never()).save(any());
    }

    @Test
    void atualizarDeveNegarAcessoAQuemNaoEOAutor() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));

        assertThatThrownBy(() -> provaService.atualizar(1L, request(1L, 10L), OUTRO_USUARIO, false))
                .isInstanceOf(AcessoNegadoException.class);
        verify(provaRepository, never()).save(any());
    }

    @Test
    void publicarDeveRevalidarAsQuestoesEMudarOStatus() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L, 11L)));
        when(provaRepository.save(any(Prova.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        ProvaResponse resultado = provaService.publicar(1L, AUTOR, false);

        assertThat(resultado.getStatus()).isEqualTo(Status.PUBLICADA);
        assertThat(caminhosRecebidos).containsExactly("/api/v1/questoes/10", "/api/v1/questoes/11");
    }

    @Test
    void publicarDeveExigirAoMenosUmaQuestao() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO)));

        assertThatThrownBy(() -> provaService.publicar(1L, AUTOR, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("prova.sem-questoes");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void publicarDeveRecusarQuandoUmaQuestaoFoiRemovidaNoServicoQuestoes() {
        materiaPorQuestao.remove(11L);
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L, 11L)));

        assertThatThrownBy(() -> provaService.publicar(1L, AUTOR, false))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("questao.nao-encontrada");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void publicarDeveRecusarProvaJaPublicada() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.PUBLICADA, 10L)));

        assertThatThrownBy(() -> provaService.publicar(1L, AUTOR, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("prova.somente-rascunho");
        verify(provaRepository, never()).save(any());
    }

    @Test
    void removerDeveMarcarAProvaESuasQuestoesComoDeletadas() {
        Prova prova = prova(1L, AUTOR, Status.PUBLICADA, 10L, 11L);
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(prova));

        provaService.remover(1L, AUTOR, false);

        verify(provaRepository).save(prova);
        assertThat(prova.getDeletadoEm()).isNotNull();
        assertThat(prova.getQuestoes()).allSatisfy(provaQuestao ->
                assertThat(provaQuestao.getDeletadoEm()).isEqualTo(prova.getDeletadoEm()));
    }

    @Test
    void removerDeveNegarAcessoAQuemNaoEOAutor() {
        when(provaRepository.findByIdAndDeletadoEmIsNull(1L))
                .thenReturn(Optional.of(prova(1L, AUTOR, Status.RASCUNHO, 10L)));

        assertThatThrownBy(() -> provaService.remover(1L, OUTRO_USUARIO, false))
                .isInstanceOf(AcessoNegadoException.class);
        verify(provaRepository, never()).save(any());
    }

    private void responder(HttpExchange exchange, int status, String json) throws IOException {
        caminhosRecebidos.add(exchange.getRequestURI().getPath());
        authorizationsRecebidos.add(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, corpo.length);
        exchange.getResponseBody().write(corpo);
        exchange.close();
    }

    private Prova prova(Long id, UUID autor, Status status, Long... questaoIds) {
        Prova prova = new Prova();
        ReflectionTestUtils.setField(prova, "id", id);
        prova.setUsuarioKeycloakId(autor);
        prova.setMateriaId(1L);
        prova.setTitulo("Prova de Cálculo I");
        prova.setDescricao("Primeira avaliação");
        prova.setStatus(status);
        for (Long questaoId : questaoIds) {
            ProvaQuestao provaQuestao = new ProvaQuestao();
            provaQuestao.setProva(prova);
            provaQuestao.setQuestaoId(questaoId);
            prova.getQuestoes().add(provaQuestao);
        }
        return prova;
    }

    private ProvaRequest request(Long materiaId, Long... questaoIds) {
        return new ProvaRequest(materiaId, "Título do request", "Descrição do request", List.of(questaoIds));
    }
}
