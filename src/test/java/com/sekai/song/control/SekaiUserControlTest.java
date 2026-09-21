package com.sekai.song.control;

import com.sekai.song.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SekaiUserControlTest {

    private final SekaiUserControl control = new SekaiUserControl();

    @Test
    void keepsAuthenticatedSessionAlive() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginUser", new User());

        assertEquals(HttpStatus.NO_CONTENT, control.keepAlive(session).getStatusCode());
    }

    @Test
    void rejectsUnauthenticatedSessionKeepAlive() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                control.keepAlive(new MockHttpSession()).getStatusCode());
    }
}
