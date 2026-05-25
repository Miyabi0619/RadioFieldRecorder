package com.miyabi0619.radiofieldrecorder.diagnostics

import com.miyabi0619.radiofieldrecorder.core.SessionSummary

object DiagnosticCommentGenerator {
    fun generate(summary: SessionSummary): List<String> {
        val comments = mutableListOf<String>()

        if (summary.probeCount == 0) {
            comments += "疎通確認のサンプルはまだ記録されていません。"
        }

        val failureRate = summary.probeFailureRate
        if (failureRate != null && failureRate >= 0.1) {
            comments += "疎通確認の失敗が見えています。DDS/ROS2より先に、Wi-Fi、AP、接続先ホスト、ファイアウォール、経路を確認してください。"
        }

        val p95Latency = summary.p95LatencyMs
        if (p95Latency != null && p95Latency >= 200L) {
            comments += "p95レイテンシが高めです。混雑、ローミング、AP負荷、接続先ホストの停止を確認してください。"
        }

        val minRssi = summary.minWifiRssi
        if (minRssi != null && minRssi <= -75) {
            comments += "Wi-Fi RSSIが弱いタイミングがあります。距離、遮蔽物、利用バンドが影響している可能性があります。"
        }

        if (summary.averageWifiRssi == null) {
            comments += "Wi-Fi RSSIを取得できていません。付近のWi-Fi/位置情報権限と端末のWi-Fi状態を確認してください。"
        }

        if (
            summary.probeCount > 0 &&
            (failureRate == null || failureRate == 0.0) &&
            (p95Latency == null || p95Latency < 100L) &&
            (summary.averageWifiRssi == null || summary.averageWifiRssi > -65.0)
        ) {
            comments += "このセッションではWi-FiとIP疎通は安定しています。DDS/ROS2/QoS/アプリ側の処理を優先して調べる状況です。"
        }

        return comments.distinct()
    }
}
