package br.com.agilizadelivery.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Motoboy;
/**
 * MotoboyRepository
 */
@Repository
public interface MotoboyRepository extends JpaRepository<Motoboy, UUID> {

    
    //** Busca um motoboy pelo CPF Hash */
    Optional<Motoboy> findByCpfHash(String cpfHash );
    //** Busca um motoboy pelo nome do usuario */
    Optional<Motoboy> findByUsuarioNome(String nome);
    //** Busca um motoboy pelo email do usuario */
    Optional<Motoboy> findByUsuarioLoginEmail(String loginEmail);

    Boolean existsByCpfHash(String cpfHash);
}