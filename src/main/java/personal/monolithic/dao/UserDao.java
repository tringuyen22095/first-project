package personal.monolithic.dao;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import personal.monolithic.constants.BundleKeys;
import personal.monolithic.entity.User;
import personal.monolithic.exception.NotFoundException;

@Repository
@RequiredArgsConstructor
public class UserDao {

    private final EntityManager em;

    @Transactional
    public User findByUsr(String usr) {
        List<User> users =
                this.em.createNamedQuery(User.NQ_FIND_BY_USR_OR_EMAIL, User.class).setParameter("usr", usr).getResultList();
        if (users.isEmpty())
            throw new NotFoundException(BundleKeys.ENTITY_NOT_FOUND);
        return users.getFirst();
    }
}
