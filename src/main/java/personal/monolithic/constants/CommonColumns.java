package personal.monolithic.constants;

public class CommonColumns {
    private CommonColumns() {
    }

    public static final String UUID_CHAR_TYPE = "org.hibernate.type.UUIDCharType";

    public static final String COL_ID = "ID";
    public static final String COL_STATE = "STATE";

    // UserInfo Columns
    public static final String COL_USER_ID = "USER_ID";
    public static final String COL_FULL_NAME = "FULL_NAME";
    public static final String COL_DOB = "DOB";
    public static final String COL_PERMANENT_ADDRESS = "PERMANENT_ADDRESS";
    public static final String COL_TEMPORARY_ADDRESS = "TEMPORARY_ADDRESS";
    public static final String COL_PHONE = "PHONE";
    public static final String COL_GENDER = "GENDER";

    // User Columns
    public static final String COL_EMAIL = "EMAIL";
    public static final String COL_USR = "USR";
    public static final String COL_PWD = "PWD";

    // Role Columns
    public static final String COL_ROLE = "ROLE";
    public static final String COL_DESCRIPTION = "DESCRIPTION";

    // Permission Columns
    public static final String COL_PERMISSION = "PERMISSION";

    // Refresh token columns
    public static final String COL_TOKEN = "TOKEN";
    public static final String COL_EXPIRES_AT = "EXPIRES_AT";
    public static final String COL_CREATED_AT = "CREATED_AT";
}
