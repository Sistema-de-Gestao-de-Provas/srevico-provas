/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.api.controller.v1;

import com.sgp.servico_provas.api.dto.request.ProvaRequest;
import com.sgp.servico_provas.api.dto.response.ProvaResponse;
import com.sgp.servico_provas.domain.enums.Status;
import com.sgp.servico_provas.domain.service.ProvaService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author diego-casemiro
 */
@RestController
@RequestMapping("/v1/provas")
@RequiredArgsConstructor
public class ProvaController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ProvaService provaService;

    // Ex.: GET /v1/provas?materiaId=1&status=RASCUNHO&titulo=prova&page=0&size=20&sort=titulo,asc
    // Campos ordenáveis: id, materiaId, titulo, status, criadoEm, atualizadoEm
    @GetMapping
    public PagedModel<ProvaResponse> listar(
            @RequestParam(required = false) Long materiaId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String titulo,
            @PageableDefault(sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
            JwtAuthenticationToken token) {
        return new PagedModel<>(provaService.listar(materiaId, status, titulo, pageable,
                keycloakId(token), admin(token)));
    }

    @GetMapping("/{id}")
    public ProvaResponse buscarPorId(@PathVariable Long id, JwtAuthenticationToken token) {
        return provaService.buscarPorId(id, keycloakId(token), admin(token));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProvaResponse criar(@Valid @RequestBody ProvaRequest request, JwtAuthenticationToken token) {
        return provaService.criar(request, keycloakId(token));
    }

    @PutMapping("/{id}")
    public ProvaResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProvaRequest request,
            JwtAuthenticationToken token) {
        return provaService.atualizar(id, request, keycloakId(token), admin(token));
    }

    // Muda só o status (RASCUNHO -> PUBLICADA), por isso PATCH e sem corpo
    @PatchMapping("/{id}/publicar")
    public ProvaResponse publicar(@PathVariable Long id, JwtAuthenticationToken token) {
        return provaService.publicar(id, keycloakId(token), admin(token));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id, JwtAuthenticationToken token) {
        provaService.remover(id, keycloakId(token), admin(token));
    }

    private UUID keycloakId(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    private boolean admin(JwtAuthenticationToken token) {
        return token.getAuthorities().stream().anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
