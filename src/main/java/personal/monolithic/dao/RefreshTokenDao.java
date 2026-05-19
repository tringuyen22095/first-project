package personal.monolithic.dao;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import personal.monolithic.entity.RefreshToken;
import personal.monolithic.entity.User;

@RequiredArgsConstructor
@Repository
public class RefreshTokenDao {

    private final EntityManager em;

    @Transactional
    public void save(RefreshToken refreshToken) {
        this.em.merge(refreshToken);
    }

    @Transactional
    public Optional<RefreshToken> findByToken(String token) {
        List<RefreshToken> refreshTokens =
                this.em.createNamedQuery(RefreshToken.NQ_FIND_BY_TOKEN, RefreshToken.class)
                        .setParameter("token", token)
                        .getResultList();
        return refreshTokens.stream().findFirst();
    }

    @Transactional
    public void delete(RefreshToken refreshToken) {
        RefreshToken managed = this.em.contains(refreshToken) ? refreshToken : this.em.merge(refreshToken);
        this.em.remove(managed);
    }

    @Transactional
    public void deleteByUser(User user) {
        this.em.createNamedQuery(RefreshToken.NQ_DELETE_BY_USER_ID)
                .setParameter("userId", user.getId())
                .executeUpdate();
    }
}
