package br.com.agilizadelivery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.UsuarioService;

@Service
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioServiceImpl(UsuarioRepository repository) {
        this.repository = repository;
    }
}
