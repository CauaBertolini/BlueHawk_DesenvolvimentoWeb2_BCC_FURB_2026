package com.example.blue_hawk.application.usecase.match;

import com.example.blue_hawk.application.usecase.auth.Access;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.entity.match.Match;

import com.example.blue_hawk.application.dto.match.DeleteMatchCommand;
import com.example.blue_hawk.domain.repository.IMatchRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteMatchUseCaseImpl implements DeleteMatchUseCase {

    private final IMatchRepository matchRepository;

        private final IUserBoardGameRepository userBoardGameRepository;

    public DeleteMatchUseCaseImpl(IMatchRepository matchRepository, IUserBoardGameRepository userBoardGameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    public void handle(DeleteMatchCommand command) {
        UUID id = UUID.fromString(command.id());
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Match not found with id: " + command.id()));
        // Só o anfitrião (dono da cópia da partida) ou ADMIN apaga
        Access.requireMatchHost(match, userBoardGameRepository, command.currentUser());
        matchRepository.deleteById(id);
    }
}
