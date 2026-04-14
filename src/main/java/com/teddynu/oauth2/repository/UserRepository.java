package com.teddynu.oauth2.repository;

import com.teddynu.oauth2.entity.OAuthProvider;
import com.teddynu.oauth2.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
