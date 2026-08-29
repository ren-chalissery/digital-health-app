import Testing

@testable import SimplicityAuth

@Suite("PasswordPolicy")
struct PasswordPolicyTests {

    @Test("accepts a password meeting the pool's rule")
    func acceptsValid() {
        #expect(PasswordPolicy.validate("Sup3rSecretPass") == nil)
    }

    @Test("refuses anything shorter than twelve characters")
    func refusesShort() {
        #expect(PasswordPolicy.validate("Sh0rtPass") != nil)
    }

    @Test("refuses a password with no uppercase letter")
    func refusesWithoutUppercase() {
        #expect(PasswordPolicy.validate("sup3rsecretpass") != nil)
    }

    @Test("refuses a password with no lowercase letter")
    func refusesWithoutLowercase() {
        #expect(PasswordPolicy.validate("SUP3RSECRETPASS") != nil)
    }

    @Test("refuses a password with no number")
    func refusesWithoutNumber() {
        #expect(PasswordPolicy.validate("SuperSecretPass") != nil)
    }
}
