package personal.monolithic.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.monolithic.dao.UserDao;
import personal.monolithic.dto.user.CurrentUserResponse;
import personal.monolithic.entity.User;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        log.info("Loading current user profile username={}", username);
        User user = this.userDao.findByUsr(username);

        List<String> roles = user.getRoles().stream().map(role -> role.getRole()).sorted().toList();

        return new CurrentUserResponse(user.getUserInfo() != null ? user.getUserInfo().getFullName() : null,
                user.getUserInfo() != null ? user.getUserInfo().getDob() : null,
                user.getUserInfo() != null ? user.getUserInfo().getPhone() : null,
                user.getUserInfo() != null && user.getUserInfo().getGender() != null ? user.getUserInfo().getGender().name() : null,
                roles);
    }
}
