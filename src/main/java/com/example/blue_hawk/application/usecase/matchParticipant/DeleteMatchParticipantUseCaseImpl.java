package com.example.blue_hawk.application.usecase.matchParticipant;

import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IMatchRepository;
import com.example.blue_hawk.domain.entity.matchparticipant.MatchParticipant;

import com.example.blue_hawk.application.dto.matchParticipant.DeleteMatchParticipantCommand;
import com.example.blue_hawk.domain.repository.IMatchParticipantRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteMatchParticipantUseCaseImpl implements DeleteMatchParticipantUseCase {

    private final IMatchParticipantRepository matchParticipantRepository;

        private final IMatchRepository matchRepository;
    private final IUserBoardGameRepository userBoardGameRepository;

    public DeleteMatchParticipantUseCaseImpl(IMatchParticipantRepository matchParticipantRepository, IMatchRepository matchRepository, IUserBoardGameRepository userBoardGameRepository) {
        this.matchRepository = matchRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    @Override
    public void handle(DeleteMatchParticipantCommand command) {
        UUID id = UUID.fromString(command.id());
        MatchParticipant participant = matchParticipantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("MatchParticipant not found with id: " + command.id()));
        MatchParticipantAccess.requireCanManage(participant, command.currentUser(), matchRepository,
                userBoardGameRepository);
        matchParticipantRepository.deleteById(id);
    }
}
