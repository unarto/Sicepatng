package com.sicepat.xrayapp.dto

data class OutboundTrafficStat(
    val tag: String,
    val direction: String,
    val value: Long,
)