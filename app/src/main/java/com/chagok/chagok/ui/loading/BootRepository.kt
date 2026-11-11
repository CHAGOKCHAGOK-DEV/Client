package com.chagok.chagok.ui.loading

import kotlinx.coroutines.delay

/** S-01 로딩 실패 종류 (화면설계서 S-01 "실패 종류별 문구" 표) */
enum class FailReason { CAR_CONN, PERM_READ, STORE_READ, CAR_UNSUPPORTED }

class BootException(val reason: FailReason) : Exception(reason.name)

/** 3단계에서 읽어 오는 로컬 설정 */
data class BootSettings(
    val consentDone: Boolean,   // 부모 동의 완료 (동의 문서 버전까지 같음)
    val profileExists: Boolean, // 부기장 프로필 있음
)

/**
 * 로딩 3단계에서 하는 일. 실패하면 BootException(reason)을 던진다.
 * 지금은 FakeBootRepository로 돌리고, Pleos SDK 문서를 받으면 실제 구현으로 교체.
 */
interface BootRepository {
    /** 1단계: Vehicle SDK 연결 + 지원 기능(TurnSignal·Wiper·속도·NaviHelper) 조회·저장 */
    suspend fun connectCar()

    /** 2단계: 권한 3종(차량 신호·마이크·음성) 상태 읽기. 모두 승인이면 true */
    suspend fun readPermissions(): Boolean

    /** 3단계: 로컬 동의·프로필 읽기 + 서버 미션팩 버전 확인(서버 실패는 무시) */
    suspend fun loadSettings(): BootSettings
}

/** SDK 연결 전 임시 구현. failAt에 값을 넣으면 그 단계에서 실패한다(실패 화면 테스트용). */
class FakeBootRepository(
    private val failAt: FailReason? = null,
) : BootRepository {

    override suspend fun connectCar() {
        delay(800)
        if (failAt == FailReason.CAR_CONN) throw BootException(FailReason.CAR_CONN)
    }

    override suspend fun readPermissions(): Boolean {
        delay(800)
        if (failAt == FailReason.PERM_READ) throw BootException(FailReason.PERM_READ)
        return true
    }

    override suspend fun loadSettings(): BootSettings {
        delay(800)
        if (failAt == FailReason.STORE_READ) throw BootException(FailReason.STORE_READ)
        return BootSettings(consentDone = true, profileExists = true)
    }
}