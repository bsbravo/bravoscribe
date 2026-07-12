package com.bravoscribe.android.ui.auth.login

import com.bravoscribe.android.MainDispatcherExtension
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class LoginViewModelTest {

    private val authRepository = mockk<AuthRepository>()
    private lateinit var viewModel: LoginViewModel

    private val user = User(
        id = "user-1",
        name = "Android Tester",
        email = "tester@example.com",
        role = "USER",
        active = true,
        createdAt = "2026-01-01T00:00:00Z",
    )

    @BeforeEach
    fun setUp() {
        viewModel = LoginViewModel(authRepository)
    }

    @Test
    fun `invalid email blocks submission without calling the repository`() {
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("password123")

        viewModel.login {}

        assertEquals("Enter a valid email address", viewModel.uiState.value.emailError)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `blank password blocks submission without calling the repository`() {
        viewModel.onEmailChange("tester@example.com")

        viewModel.login {}

        assertEquals("Enter your password", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `successful login clears submitting state and invokes onSuccess with the user`() {
        coEvery { authRepository.login("tester@example.com", "password123") } returns Result.success(user)
        viewModel.onEmailChange("tester@example.com")
        viewModel.onPasswordChange("password123")

        var result: User? = null
        viewModel.login { result = it }

        assertEquals(user, result)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed login surfaces a generic error and does not invoke onSuccess`() {
        coEvery { authRepository.login(any(), any()) } returns Result.failure(RuntimeException("401"))
        viewModel.onEmailChange("tester@example.com")
        viewModel.onPasswordChange("wrong-password")

        var invoked = false
        viewModel.login { invoked = true }

        assertFalse(invoked)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals("Invalid email or password", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `changing email clears a previous email error`() {
        viewModel.onEmailChange("bad")
        viewModel.login {}
        assertNotNull(viewModel.uiState.value.emailError)

        viewModel.onEmailChange("still-typing")

        assertNull(viewModel.uiState.value.emailError)
    }
}
