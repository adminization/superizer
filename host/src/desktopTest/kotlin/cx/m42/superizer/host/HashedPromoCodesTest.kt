package cx.m42.superizer.host

import cx.m42.superizer.Superizer
import cx.m42.superizer.activation.Activation
import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.activation.HashedPromoCodes
import cx.m42.superizer.host.activation.LocalPromoCodes
import cx.m42.superizer.host.activation.Sha256
import cx.m42.superizer.testing.FakeDeviceAuthenticator
import cx.m42.superizer.testing.FakeNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Codes kept as hashes (Unitool idea/09, D256). Every expected value here was computed by Python's
 * `hashlib`, not by the code under test — the point is that `scripts/promo-hash.sh` and this agree.
 */
class HashedPromoCodesTest {

    @Test
    fun sha256MatchesTheStandardVectorsAcrossBlockBoundaries() {
        val vectors = mapOf(
            "" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "abc" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" to
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            // 55 bytes is the longest message whose padding fits its own block; 56 spills into a
            // second; 64 is exactly one block of message and a whole block of padding.
            "a".repeat(55) to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            "a".repeat(56) to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            "a".repeat(64) to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            "a".repeat(1000) to "41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3",
        )
        vectors.forEach { (message, expected) ->
            assertEquals(expected, Sha256.hex(message.encodeToByteArray()), "length ${message.length}")
        }
    }

    @Test
    fun theKeyIsTheSaltedHashOfTheNormalisedCode() {
        // Normalised first, as LocalPromoCodes does: case, spaces and punctuation are not part of a code.
        val expected = "73363b26df93fd82d56f3343f90227d511c3116d2766d164e06c42bc861dfabf"
        assertEquals(expected, HashedPromoCodes.hash("test:", "Vault 2026"))
        assertEquals(expected, HashedPromoCodes.hash("test:", "VAULT-2026"))
        assertEquals(expected, HashedPromoCodes.hash("test:", "vault2026"))
        // Letters are letters in any script: a Cyrillic code is upper-cased like a Latin one.
        assertEquals("e03b66eee7216f06062165a0cf5909c27e33025fcdf8ea5f8933f83d1340e993", HashedPromoCodes.hash("test:", "ключ-7"))
    }

    @Test
    fun aCodeInBracketsIsHashedAsWrittenAndMatchesOnlyAsWritten() = runBlocking<Unit> {
        // As written, brackets included — not normalised (the normalised key would be ad0f0edb…).
        assertEquals("603fcb436e80baeb008690e552509ee18c5aef2f73e2f317ec4f09e06638e89d", HashedPromoCodes.hash("test:", "{ssh-keys}"))
        assertEquals(HashedPromoCodes.hash("test:", "{ssh-keys}"), HashedPromoCodes.hash("test:", "  {ssh-keys} "))
        assertEquals("ad0f0edbad738e1ae420c48ea01c65a738b7b7422113f73eca5ab5d65ea9c85d", HashedPromoCodes.hash("test:", "ssh-keys"))

        val keys = ActivationResult.OpenHostScreen("ssh-keys")
        val vault = ActivationResult.Success(Activation(AppId("vault")))
        val codes = HashedPromoCodes(
            "test:",
            mapOf(
                HashedPromoCodes.hash("test:", "{ssh-keys}") to keys,
                HashedPromoCodes.hash("test:", "VAULT-2026") to vault,
            ),
        )
        assertEquals(keys, codes.resolve("{ssh-keys}"))
        assertEquals(keys, codes.resolve(" {ssh-keys} "), "the spaces a keyboard adds")
        listOf("ssh-keys", "SSH KEYS", "{SSH-KEYS}", "{ssh keys}", "[ssh-keys]", "{ssh-keys").forEach { miss ->
            assertEquals(ActivationResult.Rejected(ActivationResult.Reason.UnknownCode), codes.resolve(miss), miss)
        }
        // An ordinary code stays forgiving, brackets and all.
        assertEquals(vault, codes.resolve("[vault 2026]"))

        // The plain table follows the same rule.
        val plain = LocalPromoCodes(mapOf("{ssh-keys}" to keys, "VAULT-2026" to vault))
        assertEquals(keys, plain.resolve("{ssh-keys}"))
        assertEquals(ActivationResult.Rejected(ActivationResult.Reason.UnknownCode), plain.resolve("ssh-keys"))
        assertEquals(vault, plain.resolve("{vault-2026}"))
    }

    @Test
    fun aTableOfHashesResolvesTheCodeAndNothingElse() = runBlocking<Unit> {
        val opened = ActivationResult.Success(Activation(AppId("vault")))
        val codes = HashedPromoCodes("test:", mapOf(HashedPromoCodes.hash("test:", "VAULT-2026") to opened))

        assertEquals(opened, codes.resolve("vault 2026"))
        assertEquals(ActivationResult.Rejected(ActivationResult.Reason.UnknownCode), codes.resolve("vault 2027"))
        // The hash itself is not a code, and the same code under another salt is another key.
        assertIs<ActivationResult.Rejected>(codes.resolve(HashedPromoCodes.hash("test:", "VAULT-2026")))
        val otherSalt = HashedPromoCodes("prod:", mapOf(HashedPromoCodes.hash("test:", "VAULT-2026") to opened))
        assertIs<ActivationResult.Rejected>(otherSalt.resolve("VAULT-2026"))
    }

    @Test
    fun aWholeHostRunsOnHashesTheServiceMenuAndASecretAppIncluded() = runBlocking<Unit> {
        isolatePrefs()
        val salt = "test:"
        val superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo(debug = false))
            network(FakeNetwork())
            deviceAuthenticator(FakeDeviceAuthenticator())
            register(StubApp("vault", secret = true))
            // No `serviceCode(…)`: the Service Menu's code is one more hashed row (D20, D256).
            promoCodes(
                HashedPromoCodes(
                    salt,
                    mapOf(
                        HashedPromoCodes.hash(salt, "SERVICE") to ActivationResult.HostCommand(ActivationResult.Command.OpenServiceMenu),
                        HashedPromoCodes.hash(salt, "VAULT-2026") to ActivationResult.Success(Activation(AppId("vault"))),
                    ),
                ),
            )
        }

        assertEquals(ActivationResult.HostCommand(ActivationResult.Command.OpenServiceMenu), superizer.activation.fromPromo("service"))
        val code = assertIs<ActivationResult.Success>(superizer.activation.fromPromo("vault-2026"))
        assertTrue(superizer.activation.apply(code.activation, source = "promo").isSuccess)
        assertEquals(AppId("vault"), superizer.handler.current.value?.app?.id)
    }

    @Test
    fun aCodeForAHostScreenResolvesOnlyWhenTheHostHasThatScreen() = runBlocking<Unit> {
        isolatePrefs()
        val salt = "test:"
        val keys = object : cx.m42.superizer.HostScreen {
            override val id = "ssh-keys"
            override fun title(langTag: String) = "Keys"

            @androidx.compose.runtime.Composable
            override fun Content(superizer: Superizer, back: () -> Unit, open: (cx.m42.superizer.HostScreen) -> Unit) = Unit
        }
        fun build(withScreen: Boolean) = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo())
            network(FakeNetwork())
            deviceAuthenticator(FakeDeviceAuthenticator())
            if (withScreen) hostScreen(keys)
            promoCodes(HashedPromoCodes(salt, mapOf(HashedPromoCodes.hash(salt, "{ssh-keys}") to ActivationResult.OpenHostScreen("ssh-keys"))))
        }

        val with = build(withScreen = true)
        assertEquals(ActivationResult.OpenHostScreen("ssh-keys"), with.activation.fromPromo("{ssh-keys}"))
        assertEquals(keys, with.codeScreens["ssh-keys"])
        // Bracketed, so exact: a near miss opens nothing.
        assertEquals(ActivationResult.Rejected(ActivationResult.Reason.UnknownCode), with.activation.fromPromo("ssh keys"))

        val without = build(withScreen = false)
        assertEquals(ActivationResult.Rejected(ActivationResult.Reason.UnknownCode), without.activation.fromPromo("{ssh-keys}"))
    }

    @Test
    fun aKeyThatIsNotAHashIsABuildMistake() {
        // A plain code pasted where its hash belongs would never match anything, silently.
        assertFailsWith<IllegalArgumentException> {
            HashedPromoCodes("test:", mapOf("VAULT-2026" to ActivationResult.Rejected(ActivationResult.Reason.UnknownCode)))
        }
    }
}
