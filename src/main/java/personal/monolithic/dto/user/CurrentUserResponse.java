package personal.monolithic.dto.user;

import java.time.LocalDate;
import java.util.List;

public record CurrentUserResponse(String fullName, LocalDate dob, String phone, String gender, List<String> roles) {
}
