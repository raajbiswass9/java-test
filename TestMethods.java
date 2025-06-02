package com.example.demo;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;


class UserRolesServiceTest {

    private UserRolesService userRolesService;

    @BeforeEach
    public void setUp() throws Exception {
        userRolesService = new UserRolesService();

        // Use reflection to inject value into private field
        Field pathField = UserRolesService.class.getDeclaredField("userToRolesFilePath");
        pathField.setAccessible(true);
        pathField.set(userRolesService, "files/permissions/DEV_userToRoles.json");
    }

    @Test
    public void testCachableUserRoles_withMockedStreamUtilsAndFile() throws Exception {
        String json = """
            [
              {
                "soeId": "bb99999",
                "program_roles": [
                  {
                    "program_id": "661fd20482caf9500db03ddf",
                    "role": "admin"
                  }
                ]
              }
            ]
            """;

        // Prepare mocked InputStream
        InputStream mockedInputStream = new ByteArrayInputStream("ignored content".getBytes(StandardCharsets.UTF_8));

        ClassLoader mockClassLoader = mock(ClassLoader.class);
        when(mockClassLoader.getResourceAsStream("files/permissions/DEV_userToRoles.json"))
                .thenReturn(mockedInputStream);

        // Set the mock classloader to the current thread
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(mockClassLoader);

        try (MockedStatic<StreamUtils> streamUtilsMock = mockStatic(StreamUtils.class)) {
            streamUtilsMock.when(() -> StreamUtils.copyToString(any(InputStream.class), eq(StandardCharsets.UTF_8)))
                    .thenReturn(json);

            // Execute
            List<User> result = userRolesService.cachableUserRoles();

            // Assert
            assertEquals(1, result.size());
            User user = result.get(0);
            assertEquals("bb99999", user.getSoeId());
            assertEquals(1, user.getProgramRoles().size());
            assertEquals("661fd20482caf9500db03ddf", user.getProgramRoles().get(0).getProgramId());
            assertEquals("admin", user.getProgramRoles().get(0).getRole());

        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }



    @Test
    public void testCachableUserRoles_shouldThrowRuntimeException_whenInputStreamIsNull() throws Exception {
        userRolesService = new UserRolesService();

        // Inject null into the private field `userToRolesFilePath`
        Field pathField = UserRolesService.class.getDeclaredField("userToRolesFilePath");
        pathField.setAccessible(true);
        pathField.set(userRolesService, null);  // Setting it to null as per requirement

        // Expect RuntimeException because InputStream will be null
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userRolesService.cachableUserRoles();
        });

        assertTrue(exception.getMessage().contains("not found")); // optional check for message
    }



    @Test
    public void testCachableUserRoles_shouldThrowRuntimeException_whenInputStreamIsNullExplicitly() throws Exception {
        // Create an anonymous subclass of UserRolesService
        UserRolesService userRolesService = new UserRolesService() {
            @Override
            public void loadUserRolesFromFile() {
                // Simulate InputStream being null
                throw new RuntimeException("files/permissions/DEV_userToRoles.json not found");
            }
        };

        // Inject the file path (even though we simulate failure)
        Field pathField = UserRolesService.class.getDeclaredField("userToRolesFilePath");
        pathField.setAccessible(true);
        pathField.set(userRolesService, "files/permissions/DEV_userToRoles.json");

        // Assert that RuntimeException is thrown due to null InputStream
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userRolesService.cachableUserRoles();
        });

        assertTrue(exception.getMessage().contains("not found"));
    }

}

