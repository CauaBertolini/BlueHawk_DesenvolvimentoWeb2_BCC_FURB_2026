package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.dto.user.GetUserQuery;
import com.example.blue_hawk.application.dto.user.UserOutput;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class GetUserUseCaseImpl implements GetUserUseCase {

    private final IUserRepository userRepository;

    public GetUserUseCaseImpl(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserOutput handle(GetUserQuery query) {
        UUID id = UUID.fromString(query.id());
        // E-mail e papel só para o próprio usuário ou um ADMIN; os demais veem o nome
        boolean full = query.currentUser() != null && query.currentUser().isSelfOrAdmin(id);
        return userRepository.findById(id)
                .map(user -> full ? UserOutput.from(user) : UserOutput.publicView(user))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
