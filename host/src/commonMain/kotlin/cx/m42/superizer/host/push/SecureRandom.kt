package cx.m42.superizer.host.push

/** The platform's CSPRNG. Only the device secret (`ds_…`) is drawn from it. */
internal expect fun secureRandomBytes(size: Int): ByteArray
