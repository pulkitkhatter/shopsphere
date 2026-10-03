package com.shopsphere.auth.repo;

import com.shopsphere.auth.model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {
    void deleteByUsername(String username);
}
