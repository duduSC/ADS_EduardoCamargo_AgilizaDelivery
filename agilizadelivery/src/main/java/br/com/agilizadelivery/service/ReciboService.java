package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.ReciboRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReciboService {

    private final ReciboRepository reciboRepository;
}
