package com.chagok.chagok.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 연령 코드 = 서버 코드 (CopilotRequest.ageGroup, 미션팩 ageContent 키) */
class AgeGroupTest {

    @Test fun `서버 코드와 같다`() {
        assertEquals(listOf("AGE_5_6", "AGE_7_8", "AGE_9_PLUS"), AgeGroup.entries.map { it.code })
    }

    @Test fun `코드로 찾기`() {
        assertEquals(AgeGroup.AGE_5_6, AgeGroup.fromCode("AGE_5_6"))
        assertEquals(AgeGroup.AGE_9_PLUS, AgeGroup.fromCode("AGE_9_PLUS"))
        assertNull(AgeGroup.fromCode(null))
        assertNull(AgeGroup.fromCode("AGE_10"))
    }

    @Test fun `예전 앱이 저장한 AGE_9P도 9세 이상으로 읽는다`() {
        assertEquals(AgeGroup.AGE_9_PLUS, AgeGroup.fromCode("AGE_9P"))
    }
}
