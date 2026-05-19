package personal.monolithic.entity;

import static personal.monolithic.constants.CommonColumns.COL_DESCRIPTION;
import static personal.monolithic.constants.CommonColumns.COL_PERMISSION;
import static personal.monolithic.constants.Constants.NORMAL_COLUMN_LENGTH;

import java.io.Serializable;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import personal.monolithic.constants.PermissionEnum;
import personal.monolithic.entity.base.BaseEntity;

@SQLDelete(sql = "UPDATE T_PERMISSION " + "SET STATE = 'DELETED' " + "WHERE ID = ?")
@SQLRestriction("state = 'ACTIVE'")
@Table(name = "T_PERMISSION")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@Entity
public class Permission extends BaseEntity implements Serializable {

    @Id
    @Column(name = COL_PERMISSION, length = NORMAL_COLUMN_LENGTH, nullable = false)
    @Enumerated(EnumType.STRING)
    private PermissionEnum permission;

    @Column(name = COL_DESCRIPTION, length = NORMAL_COLUMN_LENGTH)
    private String description;

    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    private Set<Role> roles;

}

