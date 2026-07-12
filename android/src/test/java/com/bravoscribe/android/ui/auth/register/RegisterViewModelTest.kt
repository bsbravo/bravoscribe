package com.bravoscribe.android.ui.auth.register

import com.bravoscribe.android.MainDispatcherExtension
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.HttpException
import retrofit2.Response

@ExtendWith(MainDispatcherExtension::class)
class RegisterViewModelTest {

    private val authRepository = mockk<AuthRepository>()
    private lateinit var viewModel: RegisterViewModel

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
        viewModel = RegisterViewModel(authRepository)
    }

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `field validation errors block submission without calling the repository`() {
        viewModel.onNameChange("A")
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("short")

        viewModel.register {}

        val state = viewModel.uiState.value
        assertEquals("Name must be at least 2 characters", state.nameError)
        assertEquals("Enter a valid email address", state.emailError)
        assertEquals("Password must be 8–128 characters", state.passwordError)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `successful registration invokes onSuccess with the user`() {
        coEvery { authRepository.register("Android Tester", "tester@example.com", "password123") } returns
            Result.success(user)
        viewModel.onNameChange("Android Tester")
        viewModel.onEmailChange("tester@example.com")
        viewModel.onPasswordChange("password123")

        var result: User? = null
        viewModel.register { result = it }

        assertEquals(user, result)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `409 conflict reports that the email is already registered`() {
        coEvery { authRepository.register(any(), any(), any()) } returns Result.failure(httpException(409))
        viewModel.onNameChange("Android Tester")
        viewModel.onEmailChange("tester@example.com")
        viewModel.onPasswordChange("password123")

        viewModel.register {}

        assertEquals("An account with that email already exists", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `400 from the backend reports the password constraint`() {
        coEvery { authRepository.register(any(), any(), any()) } returns Result.failure(httpException(400))
        viewModel.onNameChange("Android Tester")
        viewModel.onEmailChange("tester@example.com")
        viewModel.onPasswordChange("password123")

        viewModel.register {}

        assertEquals("Password must be at least 8 characters", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `unexpected failures report a generic message`() {
        coEvery { authRepository.register(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
        viewModel.onNameChange("Android Tester")
        viewModel.onEmailChange("tester@example.com")
        viewModel.onPasswordChange("password123")

        viewModel.register {}

        assertEquals("Something went wrong. Please try again.", viewModel.uiState.value.errorMessage)
    }
}
