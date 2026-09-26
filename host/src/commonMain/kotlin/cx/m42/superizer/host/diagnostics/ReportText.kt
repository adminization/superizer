package cx.m42.superizer.host.diagnostics

import cx.m42.superizer.diagnostics.DiagnosticsReport
import cx.m42.superizer.diagnostics.Subject
import cx.m42.superizer.runtime.HostInfo

/**
 * The report a person shares so that a problem can be fixed (06 §5): English, fixed, codes and
 * facts and the platform's raw errors. It is for whoever reads the report, not for the person, so it
 * is not translated — and it is a format, which is why it is here and its words are not.
 *
 * What is in it is only what a [cx.m42.superizer.diagnostics.Finding] carries, and findings carry no
 * secret by construction (I20); the host's self-test scans this text for canaries all the same.
 */
internal object ReportText {

    fun format(host: HostInfo, report: DiagnosticsReport): String = buildString {
        appendLine("Storage & security report")
        appendLine("at: ${iso(report.at)}")
        appendLine("host: ${host.name} ${host.version} (${host.build}) contract ${host.contractVersion}")
        appendLine("platform: ${report.platform}")
        report.device.model?.let { appendLine("model: $it") }
        report.device.os?.let { appendLine("os: $it") }
        if (report.device.facts.isNotEmpty()) {
            appendLine("device:")
            report.device.facts.entries.sortedBy { it.key }.forEach { (k, v) -> appendLine("  $k = $v") }
        }
        appendLine("worst: ${report.worst}")
        appendLine("findings:")
        if (report.findings.isEmpty()) appendLine("  (none)")
        report.findings.forEach { f ->
            append("  [${f.status}] ${f.code} · ${subject(f.subject)}")
            if (f.facts.isNotEmpty()) append(" · " + f.facts.entries.joinToString(", ") { "${it.key}=${it.value}" })
            if (f.actions.isNotEmpty()) append(" · actions: " + f.actions.joinToString(","))
            appendLine()
            f.evidence?.let { appendLine("      evidence: ${oneLine(it)}") }
        }
        val test = report.selfTest
        if (test == null) {
            appendLine("self-test: never run")
        } else {
            appendLine("self-test: ${if (test.passed) "passed" else "FAILED"} at ${iso(test.at)} on ${test.hostVersion}")
            test.steps.forEach { s ->
                val mark = when {
                    !s.ran -> "skip"
                    s.passed -> " ok "
                    else -> "FAIL"
                }
                append("  [$mark] ${s.code} ${s.millis} ms")
                s.evidence?.let { append(" · ${oneLine(it)}") }
                appendLine()
            }
        }
    }

    private fun subject(subject: Subject): String = when (subject) {
        Subject.HostSecrets -> "host-secrets"
        is Subject.Key -> "key ${subject.id}"
        is Subject.AppSecrets -> "app ${subject.appId}"
        Subject.Backup -> "backup"
        Subject.Device -> "device"
        Subject.SelfTest -> "self-test"
    }

    private fun oneLine(text: String): String = text.replace('\n', ' ').take(500)

    /** `2026-09-26T05:49:23Z`, without a date library: the civil-from-days algorithm (Hinnant). */
    fun iso(millis: Long): String {
        val seconds = millis.floorDiv(1000L)
        val days = seconds.floorDiv(86_400L)
        val secondOfDay = seconds.mod(86_400L)
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        fun two(n: Long) = n.toString().padStart(2, '0')
        return "$year-${two(month)}-${two(day)}T${two(secondOfDay / 3600)}:${two(secondOfDay % 3600 / 60)}:${two(secondOfDay % 60)}Z"
    }
}
