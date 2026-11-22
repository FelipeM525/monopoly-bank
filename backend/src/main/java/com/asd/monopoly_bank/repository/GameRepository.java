package com.asd.monopoly_bank.repository;

import com.asd.monopoly_bank.model.Game;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

	List<Game> findAllByOrderByCreatedAtDescIdDesc();
}
