package com.example.blue_hawk.application.usecase.matchParticipant;

import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IMatchRepository;

import com.example.blue_hawk.application.dto.matchParticipant.UpdateMatchParticipantCommand;
import com.example.blue_hawk.application.dto.matchParticipant.UpdateMatchParticipantOutput;
import com.example.blue_hawk.domain.entity.matchparticipant.MatchParticipant;
import com.example.blue_hawk.domain.repository.IMatchParticipantRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateMatchParticipantUseCaseImpl implements UpdateMatchParticipantUseCase {

    private final IMatchParticipantRepository matchParticipantRepository;

        private final IMatchRepository matchRepository;
    private final IUserBoardGameRepository userBoardGameRepository;

    public UpdateMatchParticipantUseCaseImpl(IMatchParticipantRepository matchParticipantRepository, IMatchRepository matchRepository, IUserBoardGameRepository userBoardGameRepository) {
        this.matchRepository = matchRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    @Override
    public UpdateMatchParticipantOutput handle(UpdateMatchParticipantCommand command) {
        MatchParticipant matchParticipant = matchParticipantRepository
                .findById(UUID.fromString(command.id()))
                .orElseThrow(() -> new RuntimeException("MatchParticipant not found with id: " + command.id()));
        MatchParticipantAccess.requireCanManage(matchParticipant, command.currentUser(), matchRepository,
                userBoardGameRepository);
        UUID playerId = MatchParticipantAccess.requireJoinAsSelf(command.matchId(), command.userId(),
                command.currentUser());

        matchParticipant.setMatchId(UUID.fromString(command.matchId()));
        matchParticipant.setUserId(playerId);

        MatchParticipant updated = matchParticipantRepository.save(matchParticipant);

        return new UpdateMatchParticipantOutput(
                updated.getId().toString(),
                updated.getMatchId().toString(),
                updated.getUserId().toString()
        );
    }
}
