package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.ReviewBoardgameCommand;

public interface ReviewBoardgameUseCase {
    GetBoardgameOutput handle(ReviewBoardgameCommand command);
}
