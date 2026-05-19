package personal.monolithic.entity;

import static personal.monolithic.constants.CommonColumns.COL_CREATED_AT;
import static personal.monolithic.constants.CommonColumns.COL_EXPIRES_AT;
import static personal.monolithic.constants.CommonColumns.COL_TOKEN;
import static personal.monolithic.constants.CommonColumns.COL_USER_ID;
import static personal.monolithic.constants.Constants.NORMAL_COLUMN_LENGTH;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "T_REFRESH_TOKEN")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@NamedQueries({
        @NamedQuery(name = RefreshToken.NQ_FIND_BY_TOKEN,
                    query = "FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.token = :token"),
        @NamedQuery(name = RefreshToken.NQ_DELETE_BY_USER_ID,
                    query = "DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
})
public class RefreshToken implements Serializable {

    public static final String NQ_FIND_BY_TOKEN = "RefreshToken.NQ_FIND_BY_TOKEN";
    public static final String NQ_DELETE_BY_USER_ID = "RefreshToken.NQ_DELETE_BY_USER_ID";

    @Id
    @Column(name = COL_TOKEN, length = NORMAL_COLUMN_LENGTH, nullable = false)
    private String token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = COL_USER_ID, nullable = false, unique = true)
    private User user;

    @Column(name = COL_EXPIRES_AT, nullable = false)
    private Instant expiresAt;

    @Column(name = COL_CREATED_AT, nullable = false)
    private Instant createdAt;

}
