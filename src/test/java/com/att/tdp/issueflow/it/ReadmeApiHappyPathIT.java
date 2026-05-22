package com.att.tdp.issueflow.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * End-to-end happy path walking the API table in {@code README.md} against a real Postgres
 * instance booted via Testcontainers. Exercises auth → users → projects → tickets → comments
 * with mentions → audit log → logout. Slice/unit tests continue to run on H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReadmeApiHappyPathIT {

    // Started eagerly in a static block so @DynamicPropertySource resolves
    // against a running container (Spring evaluates the registry before
    // the @Testcontainers extension's beforeAll callback fires).
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("issueflow")
        .withUsername("issueflow")
        .withPassword("issueflow");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @LocalServerPort int port;

    private String adminToken;
    private Long devUserId;
    private Long projectId;
    private Long ticketId;

    @BeforeAll
    void setupRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/";
    }

    @Test @Order(1)
    void loginAsBootstrapAdmin() {
        adminToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "admin12345"))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
            .body("expiresIn", greaterThan(0))
            .extract().path("accessToken");
    }

    @Test @Order(2)
    void createDeveloperUser() {
        devUserId = given().auth().oauth2(adminToken)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "username", "dev_one",
                "email", "dev1@issueflow.local",
                "fullName", "Dev One",
                "role", "DEVELOPER",
                "password", "devpass12"))
        .when()
            .post("/users")
        .then()
            .statusCode(200)
            .body("username", equalTo("dev_one"))
            .body("role", equalTo("DEVELOPER"))
            .extract().jsonPath().getLong("id");
    }

    @Test @Order(3)
    void createProjectOwnedByAdmin() {
        Long adminId = given().auth().oauth2(adminToken)
            .when().get("/auth/me")
            .then().statusCode(200).extract().jsonPath().getLong("id");

        projectId = given().auth().oauth2(adminToken)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Phoenix", "description", "Top-secret rewrite", "ownerId", adminId))
        .when()
            .post("/projects")
        .then()
            .statusCode(200)
            .body("name", equalTo("Phoenix"))
            .extract().jsonPath().getLong("id");
    }

    @Test @Order(4)
    void createTicketOnProject() {
        ticketId = given().auth().oauth2(adminToken)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "title", "First ticket",
                "description", "Smoke test ticket",
                "status", "TODO",
                "priority", "MEDIUM",
                "type", "FEATURE",
                "projectId", projectId))
        .when()
            .post("/tickets")
        .then()
            .statusCode(200)
            .body("title", equalTo("First ticket"))
            .body("status", equalTo("TODO"))
            .extract().jsonPath().getLong("id");
    }

    @Test @Order(5)
    void addCommentWithMentionAndReadTicket() {
        Long adminId = given().auth().oauth2(adminToken)
            .when().get("/auth/me")
            .then().statusCode(200).extract().jsonPath().getLong("id");

        given().auth().oauth2(adminToken)
            .contentType(ContentType.JSON)
            .body(Map.of("authorId", adminId, "content", "Hello @dev_one, please take a look!"))
        .when()
            .post("/tickets/{id}/comments", ticketId)
        .then()
            .statusCode(200)
            .body("content", equalTo("Hello @dev_one, please take a look!"))
            .body("mentionedUsers.username", hasItem("dev_one"));

        given().auth().oauth2(adminToken)
        .when()
            .get("/tickets/{id}", ticketId)
        .then()
            .statusCode(200)
            .body("id", equalTo(ticketId.intValue()))
            .body("title", equalTo("First ticket"));
    }

    @Test @Order(6)
    void auditLogContainsRecordedActions() {
        given().auth().oauth2(adminToken)
            .queryParam("entityType", "TICKET")
            .queryParam("entityId", ticketId)
        .when()
            .get("/audit-logs")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("action", hasItem("CREATE"));
    }

    @Test @Order(7)
    void logoutInvalidatesTokenForSubsequentRequests() {
        given().auth().oauth2(adminToken)
        .when()
            .post("/auth/logout")
        .then()
            .statusCode(200);

        given().auth().oauth2(adminToken)
        .when()
            .get("/auth/me")
        .then()
            .statusCode(401);
    }
}

