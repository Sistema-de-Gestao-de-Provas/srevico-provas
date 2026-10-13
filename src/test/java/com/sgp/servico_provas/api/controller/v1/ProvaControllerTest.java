/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.api.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sgp.servico_provas.api.dto.request.ProvaRequest;
import com.sgp.servico_provas.api.dto.response.ProvaQuestaoResponse;
import com.sgp.servico_provas.api.dto.response.ProvaResponse;
import com.sgp.servico_provas.config.internationalization.InternationalizationConfig;
import com.sgp.servico_provas.config.security.SecurityConfig;
import com.sgp.servico_provas.domain.enums.Status;
import com.sgp.servico_provas.domain.exception.AcessoNegadoException;
import com.sgp.servico_provas.domain.exception.RecursoNaoEncontradoException;
import com.sgp.servico_provas.domain.exception.RegraNegocioException;
import com.sgp.servico_provas.domain.exception.ServicoExternoException;
import com.sgp.servico_provas.domain.service.ProvaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 *
 * @author diego-casemiro
 */
@WebMvcTest(ProvaController.class)
@Import({SecurityConfig.class, InternationalizationConfig.class})
class ProvaControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();

    private static final String PROVA_JSON = """
            {
              "materiaId": 1,
              "titulo": "Prova de Cálculo I",
              "descricao": "Primeira avaliação",
              "questaoIds": [10, 11]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvaService provaService;

    // Os tokens dos testes já chegam prontos pelo jwt(); o decoder só existe para o contexto subir sem o Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listarDeveRepassarOsFiltrosEAPaginacaoParaOService() throws Exception {
        when(provaService.listar(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(response(Status.RASCUNHO))));

        mockMvc.perform(get("/v1/provas")
                        .param("materiaId", "1")
                        .param("status", "RASCUNHO")
                        .param("titulo", "cálculo")
                        .param("page", "1")
                        .param("size", "5")
                        .with(professor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].questoes.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(provaService).listar(eq(1L), eq(Status.RASCUNHO), eq("cálculo"), pageable.capture(), eq(USUARIO),
                eq(false));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        // Sem sort na URL, vale a ordenação padrão do @PageableDefault
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "criadoEm"));
    }

    @Test
    void listarSemFiltrosDevePassarNuloEIdentificarOAdmin() throws Exception {
        when(provaService.listar(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/provas").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(provaService).listar(isNull(), isNull(), isNull(), any(Pageable.class), eq(USUARIO), eq(true));
    }

    @Test
    void listarDeveResponder400QuandoAOrdenacaoEInvalida() throws Exception {
        when(provaService.listar(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RegraNegocioException("ordenacao.campo-invalido", "senha"));

        mockMvc.perform(get("/v1/provas").param("sort", "senha").with(professor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Não é possível ordenar pelo campo \"senha\"."));
    }

    @Test
    void listarDeveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/v1/provas"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provaService);
    }

    @Test
    void buscarPorIdDeveRetornarAProva() throws Exception {
        when(provaService.buscarPorId(1L, USUARIO, false)).thenReturn(response(Status.RASCUNHO));

        mockMvc.perform(get("/v1/provas/1").with(professor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").value("Prova de Cálculo I"))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.questoes[0].questaoId").value(10));
    }

    @Test
    void buscarPorIdDeveResponder404QuandoAProvaNaoExiste() throws Exception {
        when(provaService.buscarPorId(99L, USUARIO, false))
                .thenThrow(new RecursoNaoEncontradoException("prova.nao-encontrada", "99"));

        mockMvc.perform(get("/v1/provas/99").with(professor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Prova 99 não encontrada."));
    }

    @Test
    void buscarPorIdDeveResponder403QuandoOUsuarioNaoEOAutor() throws Exception {
        when(provaService.buscarPorId(1L, USUARIO, false))
                .thenThrow(new AcessoNegadoException("prova.somente-autor"));

        mockMvc.perform(get("/v1/provas/1").with(professor()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Apenas o autor da prova ou um administrador pode acessá-la."));
    }

    @Test
    void criarDeveResponder201ComAProvaCriadaPeloUsuarioDoToken() throws Exception {
        when(provaService.criar(any(ProvaRequest.class), eq(USUARIO))).thenReturn(response(Status.RASCUNHO));

        mockMvc.perform(post("/v1/provas")
                        .with(professor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROVA_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RASCUNHO"));

        ArgumentCaptor<ProvaRequest> request = ArgumentCaptor.forClass(ProvaRequest.class);
        verify(provaService).criar(request.capture(), eq(USUARIO));
        assertThat(request.getValue().getMateriaId()).isEqualTo(1L);
        assertThat(request.getValue().getTitulo()).isEqualTo("Prova de Cálculo I");
        assertThat(request.getValue().getQuestaoIds()).containsExactly(10L, 11L);
    }

    @Test
    void criarDeveResponder400ComOsCamposInvalidos() throws Exception {
        mockMvc.perform(post("/v1/provas")
                        .with(professor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
                .andExpect(jsonPath("$.erros.materiaId").exists())
                .andExpect(jsonPath("$.erros.titulo").exists())
                .andExpect(jsonPath("$.erros.questaoIds").exists());

        verifyNoInteractions(provaService);
    }

    @Test
    void criarDeveResponder503QuandoOServicoQuestoesEstaIndisponivel() throws Exception {
        when(provaService.criar(any(ProvaRequest.class), eq(USUARIO)))
                .thenThrow(new ServicoExternoException("servico-questoes.indisponivel"));

        mockMvc.perform(post("/v1/provas")
                        .with(professor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROVA_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("O serviço de questões está indisponível. Tente novamente mais tarde."));
    }

    @Test
    void atualizarDeveRepassarOIdEOSolicitante() throws Exception {
        when(provaService.atualizar(eq(1L), any(ProvaRequest.class), eq(USUARIO), eq(true)))
                .thenReturn(response(Status.RASCUNHO));

        mockMvc.perform(put("/v1/provas/1")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROVA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void publicarDeveRetornarAProvaPublicada() throws Exception {
        when(provaService.publicar(1L, USUARIO, false)).thenReturn(response(Status.PUBLICADA));

        mockMvc.perform(patch("/v1/provas/1/publicar").with(professor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADA"));
    }

    @Test
    void publicarDeveResponder400QuandoAProvaJaFoiPublicada() throws Exception {
        when(provaService.publicar(1L, USUARIO, false))
                .thenThrow(new RegraNegocioException("prova.somente-rascunho"));

        mockMvc.perform(patch("/v1/provas/1/publicar").with(professor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Apenas provas em rascunho podem ser alteradas ou publicadas."));
    }

    @Test
    void removerDeveResponder204() throws Exception {
        mockMvc.perform(delete("/v1/provas/1").with(professor()))
                .andExpect(status().isNoContent());

        verify(provaService).remover(1L, USUARIO, false);
    }

    private JwtRequestPostProcessor professor() {
        return jwt().jwt(jwt -> jwt.subject(USUARIO.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_PROFESSOR"));
    }

    private JwtRequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.subject(USUARIO.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ProvaResponse response(Status status) {
        return new ProvaResponse(1L, USUARIO, 1L, "Prova de Cálculo I", "Primeira avaliação", status, List.of(
                new ProvaQuestaoResponse(1L, 10L),
                new ProvaQuestaoResponse(2L, 11L)), null, null);
    }
}
