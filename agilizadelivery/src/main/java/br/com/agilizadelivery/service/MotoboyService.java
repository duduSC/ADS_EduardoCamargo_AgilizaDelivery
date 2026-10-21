package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.MotoboyRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MotoboyService {

    private final MotoboyRepository motoboyRepository;
}
