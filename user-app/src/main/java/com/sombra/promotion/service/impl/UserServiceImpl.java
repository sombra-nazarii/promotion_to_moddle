package com.sombra.promotion.service.impl;

import com.sombra.promotion.entity.Role;
import com.sombra.promotion.entity.User;
import com.sombra.promotion.enums.RoleEnum;
import com.sombra.promotion.exception.BadRequestException;
import com.sombra.promotion.exception.InternalServerException;
import com.sombra.promotion.exception.NotFoundException;
import com.sombra.promotion.repository.UserRepository;
import com.sombra.promotion.service.RoleService;
import com.sombra.promotion.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.sombra.promotion.util.Constants.*;
import static java.lang.String.format;
import static java.util.Objects.nonNull;

@Slf4j
@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final RestTemplateService restTemplateService;

    @Override
    public User getExistingUserByUserCredentialId(final Long userCredentialId) {
        return userRepository.findUserByUserCredentialId(userCredentialId)
                .orElseThrow(() -> new NotFoundException(format(USER + WITH_ID + NOT_EXIST, userCredentialId)));
    }

    @Transactional
    @Override
    public void updateUserRole(final Long userCredentialId, final Collection<String> roles) {
        final User currentUser = getCurrentUser();
        final User user = getExistingUserByUserCredentialId(userCredentialId);

        if (!currentUser.getId().equals(user.getId()) || !isUserSuperAdmin(currentUser)) {
            throw new BadRequestException("Can't update role");
        }

        final List<Role> roleList = roles.stream()
                .map(RoleEnum::valueOf)
                .map(roleService::getExistingByRoleName)
                .collect(Collectors.toList());

        user.setRoles(roleList);

        if (restTemplateService.isLoginServiceHealthy()) {
            userRepository.save(user);
            log.info("Updated roles for User Credential with id {}", user.getUserCredentialId());
        } else {
            log.error("Login service is broken!");
            throw new InternalServerException("Cant' update role");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String userCredentialId) throws UsernameNotFoundException {
        return getExistingUserByUserCredentialId(Long.valueOf(userCredentialId));
    }

    public User getCurrentUser() {
        final Object userCredential = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (nonNull(userCredential) && userCredential instanceof User) {
            return (User) userCredential;
        } else {
            throw new InternalServerException("Can't get User");
        }
    }

    private boolean isUserSuperAdmin(final User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList())
                .contains(RoleEnum.ROLE_SUPER_ADMIN);
    }
}
