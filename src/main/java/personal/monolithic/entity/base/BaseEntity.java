package personal.monolithic.entity.base;

import static personal.monolithic.constants.CommonColumns.COL_STATE;
import static personal.monolithic.constants.Constants.NORMAL_COLUMN_LENGTH;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import personal.monolithic.constants.EntityState;

@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
public class BaseEntity implements Serializable {
    @Enumerated(EnumType.STRING)
    @Column(name = COL_STATE, length = NORMAL_COLUMN_LENGTH, nullable = false)
    private EntityState state;
}
