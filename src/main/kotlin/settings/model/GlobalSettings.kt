package settings.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GlobalSettings(
    val inbound: InboundGlobalConfig = InboundGlobalConfig(),
)

data class InboundGlobalConfig(
    val listen: String = "0.0.0.0",
    val socksPort: Int = 10808,
    val socksUdpEnabled: Boolean = false,
    val socksAuth: SocksAuthConfig? = null,
    val httpPort: Int = 10809,
    val httpAuth: HttpAuthConfig? = null,
)

data class SocksAuthConfig(
    val user: String = "",
    val pass: String = "",
)

data class HttpAuthConfig(
    val user: String = "",
    val pass: String = "",
)
