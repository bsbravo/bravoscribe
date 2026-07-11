import { describe, it, expect } from "vitest"
import {
  emailSchema,
  nameSchema,
  passwordSchema,
  loginSchema,
  registerSchema,
  resetPasswordSchema,
  changePasswordSchema,
} from "./validation"

describe("emailSchema", () => {
  it("accepts a valid email", () => {
    expect(emailSchema.safeParse("user@example.com").success).toBe(true)
  })

  it("rejects an empty string", () => {
    const result = emailSchema.safeParse("")
    expect(result.success).toBe(false)
  })

  it("rejects a malformed email", () => {
    expect(emailSchema.safeParse("not-an-email").success).toBe(false)
  })
})

describe("nameSchema", () => {
  it("rejects a single-character name", () => {
    expect(nameSchema.safeParse("A").success).toBe(false)
  })

  it("accepts a 2-character name (boundary)", () => {
    expect(nameSchema.safeParse("Al").success).toBe(true)
  })

  it("rejects a name over 100 characters", () => {
    expect(nameSchema.safeParse("a".repeat(101)).success).toBe(false)
  })

  it("accepts a name at exactly 100 characters (boundary)", () => {
    expect(nameSchema.safeParse("a".repeat(100)).success).toBe(true)
  })
})

describe("passwordSchema", () => {
  it("rejects passwords under 8 characters", () => {
    expect(passwordSchema.safeParse("short1").success).toBe(false)
  })

  it("accepts exactly 8 characters (boundary)", () => {
    expect(passwordSchema.safeParse("12345678").success).toBe(true)
  })

  it("rejects passwords over 128 characters", () => {
    expect(passwordSchema.safeParse("a".repeat(129)).success).toBe(false)
  })

  it("accepts exactly 128 characters (boundary)", () => {
    expect(passwordSchema.safeParse("a".repeat(128)).success).toBe(true)
  })
})

describe("loginSchema", () => {
  it("accepts valid email + non-empty password", () => {
    const result = loginSchema.safeParse({ email: "user@example.com", password: "x" })
    expect(result.success).toBe(true)
  })

  it("rejects an empty password even though login has no minimum length rule server-side", () => {
    const result = loginSchema.safeParse({ email: "user@example.com", password: "" })
    expect(result.success).toBe(false)
  })
})

describe("registerSchema", () => {
  it("accepts a fully valid payload", () => {
    const result = registerSchema.safeParse({
      name: "Ada Lovelace",
      email: "ada@example.com",
      password: "supersecret",
    })
    expect(result.success).toBe(true)
  })

  it("reports all three fields when all are invalid", () => {
    const result = registerSchema.safeParse({ name: "A", email: "bad", password: "short" })
    expect(result.success).toBe(false)
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0])
      expect(paths).toEqual(expect.arrayContaining(["name", "email", "password"]))
    }
  })
})

describe("resetPasswordSchema", () => {
  it("accepts matching passwords", () => {
    const result = resetPasswordSchema.safeParse({
      newPassword: "supersecret",
      confirmPassword: "supersecret",
    })
    expect(result.success).toBe(true)
  })

  it("rejects mismatched passwords and flags confirmPassword", () => {
    const result = resetPasswordSchema.safeParse({
      newPassword: "supersecret",
      confirmPassword: "different",
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(["confirmPassword"])
      expect(result.error.issues[0].message).toBe("Passwords do not match")
    }
  })
})

describe("changePasswordSchema", () => {
  it("requires a non-empty current password", () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: "",
      newPassword: "supersecret",
      confirmPassword: "supersecret",
    })
    expect(result.success).toBe(false)
  })

  it("rejects mismatched new/confirm passwords", () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: "oldpassword",
      newPassword: "supersecret",
      confirmPassword: "nope",
    })
    expect(result.success).toBe(false)
  })

  it("accepts a fully valid payload", () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: "oldpassword",
      newPassword: "supersecret",
      confirmPassword: "supersecret",
    })
    expect(result.success).toBe(true)
  })
})
