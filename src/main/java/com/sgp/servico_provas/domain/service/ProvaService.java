/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.service;

import com.sgp.servico_provas.api.dto.request.ProvaRequest;
import com.sgp.servico_provas.api.dto.response.MateriaResponse;
import com.sgp.servico_provas.api.dto.response.ProvaResponse;
import com.sgp.servico_provas.api.dto.response.QuestaoResponse;
import com.sgp.servico_provas.domain.entity.Prova;
import com.sgp.servico_provas.domain.entity.ProvaQuestao;
import com.sgp.servico_provas.domain.enums.Status;
import com.sgp.servico_provas.domain.exception.AcessoNegadoException;
import com.sgp.servico_provas.domain.exception.RecursoNaoEncontradoException;
import com.sgp.servico_provas.domain.exception.RegraNegocioException;
import com.sgp.servico_provas.domain.exception.ServicoExternoException;
import com.sgp.servico_provas.domain.repository.ProvaRepository;
import com.sgp.servico_provas.domain.repository.specification.ProvaSpecification;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 *
 * @author diego-casemiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvaService {

    private static final String ENDPOINT_MATERIA_POR_ID = "/v1/materias/{id}";
    private static final String ENDPOINT_QUESTAO_POR_ID = "/v1/questoes/{id}";

    private final ProvaRepository provaRepository;
    private final ModelMapper modelMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${servico-usuarios.url}")
    private String servicoUsuariosUrl;

    @Value("${servico-questoes.url}")
    private String servicoQuestoesUrl;

    // Cada usuário só vê as próprias provas; o ADMIN vê as de todos
    @Transactional(readOnly = true)
    public Page<ProvaResponse> listar(Long materiaId, Status status, String titulo, Pageable pageable,
            UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        validarOrdenacao(pageable.getSort());
        UUID autor = solicitanteAdmin ? null : solicitanteKeycloakId;
        return provaRepository.findAll(ProvaSpecification.filtrar(materiaId, status, titulo, autor), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProvaResponse buscarPorId(Long id, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Prova prova = buscarOuFalhar(id);
        validarAutor(prova, solicitanteKeycloakId, solicitanteAdmin);
        return toResponse(prova);
    }

    public Prova buscarOuFalhar(Long id) {
        return provaRepository.findByIdAndDeletadoEmIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("prova.nao-encontrada", String.valueOf(id)));
    }

    // Toda prova nasce como rascunho; o autor vem sempre do token, nunca do corpo da requisição
    @Transactional
    public ProvaResponse criar(ProvaRequest request, UUID usuarioKeycloakId) {
        Prova prova = new Prova();
        prova.setUsuarioKeycloakId(usuarioKeycloakId);
        prova.setStatus(Status.RASCUNHO);
        aplicarRequest(prova, request);
        return toResponse(provaRepository.save(prova));
    }

    @Transactional
    public ProvaResponse atualizar(Long id, ProvaRequest request, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Prova prova = buscarOuFalhar(id);
        validarAutor(prova, solicitanteKeycloakId, solicitanteAdmin);
        validarRascunho(prova);
        aplicarRequest(prova, request);
        return toResponse(provaRepository.save(prova));
    }

    // As questões são validadas de novo, pois podem ter sido removidas ou alteradas desde que entraram no rascunho
    @Transactional
    public ProvaResponse publicar(Long id, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Prova prova = buscarOuFalhar(id);
        validarAutor(prova, solicitanteKeycloakId, solicitanteAdmin);
        validarRascunho(prova);
        if (prova.getQuestoes().isEmpty()) {
            throw new RegraNegocioException("prova.sem-questoes");
        }
        validarQuestoes(prova.getMateriaId(), prova.getQuestoes().stream().map(ProvaQuestao::getQuestaoId).toList());
        prova.setStatus(Status.PUBLICADA);
        return toResponse(provaRepository.save(prova));
    }

    @Transactional
    public void remover(Long id, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Prova prova = buscarOuFalhar(id);
        validarAutor(prova, solicitanteKeycloakId, solicitanteAdmin);
        LocalDateTime agora = LocalDateTime.now();
        prova.setDeletadoEm(agora);
        prova.getQuestoes().forEach(provaQuestao -> provaQuestao.setDeletadoEm(agora));
        provaRepository.save(prova);
    }

    // PROFESSOR e DIRETOR têm o mesmo nível: só acessam as próprias provas. O ADMIN acessa todas
    private void validarAutor(Prova prova, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        if (!solicitanteAdmin && !prova.getUsuarioKeycloakId().equals(solicitanteKeycloakId)) {
            throw new AcessoNegadoException("prova.somente-autor");
        }
    }

    // Prova publicada não muda mais: só o rascunho pode ser editado ou publicado
    private void validarRascunho(Prova prova) {
        if (prova.getStatus() != Status.RASCUNHO) {
            throw new RegraNegocioException("prova.somente-rascunho");
        }
    }

    // A matéria fica no servico-usuarios: a validação chama GET /v1/materias/{id} repassando o token de quem
    // fez a requisição, pois o endpoint exige autenticação.
    // Matéria inexistente ou removida responde 404 lá e vira 404 aqui; qualquer outra falha vira 503
    private void validarMateria(Long materiaId) {
        try {
            restClient.get()
                    .uri(servicoUsuariosUrl + ENDPOINT_MATERIA_POR_ID, materiaId)
                    .headers(headers -> headers.setBearerAuth(token()))
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                        throw new RecursoNaoEncontradoException("materia.nao-encontrada", String.valueOf(materiaId));
                    })
                    .body(MateriaResponse.class);
        } catch (RestClientException ex) {
            log.error("Erro ao consultar a matéria {} no servico-usuarios", materiaId, ex);
            throw new ServicoExternoException("servico-usuarios.indisponivel");
        }
    }

    // Cada questão precisa existir no servico-questoes, ser acessível a quem monta a prova e ser da mesma matéria
    private void validarQuestoes(Long materiaId, List<Long> questaoIds) {
        if (new HashSet<>(questaoIds).size() != questaoIds.size()) {
            throw new RegraNegocioException("prova.questao-repetida");
        }
        questaoIds.forEach(questaoId -> {
            QuestaoResponse questao = buscarQuestao(questaoId);
            if (!materiaId.equals(questao.getMateriaId())) {
                throw new RegraNegocioException("prova.questao-de-outra-materia", String.valueOf(questaoId));
            }
        });
    }

    // Sem isso, ordenar por um campo inexistente estoura um 500
    private void validarOrdenacao(Sort sort) {
        sort.stream()
                .map(Sort.Order::getProperty)
                .filter(campo -> !ProvaSpecification.CAMPOS_ORDENAVEIS.contains(campo))
                .findFirst()
                .ifPresent(campo -> {
                    throw new RegraNegocioException("ordenacao.campo-invalido", campo);
                });
    }

    // As questões são sempre substituídas pelas do request: as antigas são apagadas (orphanRemoval)
    private void aplicarRequest(Prova prova, ProvaRequest request) {
        // Na atualização, só consulta o servico-usuarios se a matéria mudou
        if (!request.getMateriaId().equals(prova.getMateriaId())) {
            validarMateria(request.getMateriaId());
        }
        validarQuestoes(request.getMateriaId(), request.getQuestaoIds());
        prova.setMateriaId(request.getMateriaId());
        prova.setTitulo(request.getTitulo());
        prova.setDescricao(request.getDescricao());

        prova.getQuestoes().clear();
        request.getQuestaoIds().forEach(questaoId -> {
            ProvaQuestao provaQuestao = new ProvaQuestao();
            provaQuestao.setProva(prova);
            provaQuestao.setQuestaoId(questaoId);
            prova.getQuestoes().add(provaQuestao);
        });
    }

    // O servico-questoes já aplica a regra de autoria: quem não é o autor da questão (nem ADMIN) recebe 403
    private QuestaoResponse buscarQuestao(Long questaoId) {
        try {
            return restClient.get()
                    .uri(servicoQuestoesUrl + ENDPOINT_QUESTAO_POR_ID, questaoId)
                    .headers(headers -> headers.setBearerAuth(token()))
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                        throw new RecursoNaoEncontradoException("questao.nao-encontrada", String.valueOf(questaoId));
                    })
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.FORBIDDEN), (request, response) -> {
                        throw new AcessoNegadoException("questao.sem-acesso", String.valueOf(questaoId));
                    })
                    .body(QuestaoResponse.class);
        } catch (RestClientException ex) {
            log.error("Erro ao consultar a questão {} no servico-questoes", questaoId, ex);
            throw new ServicoExternoException("servico-questoes.indisponivel");
        }
    }

    // Token de quem fez a requisição, repassado nas chamadas aos outros serviços
    private String token() {
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return token.getToken().getTokenValue();
    }

    private ProvaResponse toResponse(Prova prova) {
        return modelMapper.map(prova, ProvaResponse.class);
    }
}
