package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.usecase.Paging;

import com.example.blue_hawk.application.dto.user.ListUserQuery;
import com.example.blue_hawk.application.dto.user.UserOutput;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListUserUseCaseImpl implements ListUserUseCase {

    private final IUserRepository userRepository;

    public ListUserUseCaseImpl(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserOutput> handle(ListUserQuery query) {
        int page = Paging.page(query.page());
        int size = Paging.size(query.size(), 20);

        return userRepository.findAll(query.name(), query.email(), page, size)
                .map(UserOutput::from)
                .toList();
    }
}
