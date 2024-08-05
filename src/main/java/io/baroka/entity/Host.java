package io.baroka.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * -------------------------------------------------------------------------------------
 * ::::::'OO::::'OOO::::'OO:::'OO:'OO::::'OO:'OOOOOOOO:::'OOOOOOO::'OO::::'OO:'OO....OO:
 * :::::: OO:::'OO OO:::. OO:'OO:: OO::::.OO: OO.....OO:'OO.....OO: OO:::: OO: OOO...OO:
 * :::::: OO::'OO:..OO:::. OOOO::: OO::::.OO: OO::::.OO: OO::::.OO: OO:::: OO: OOOO..OO:
 * :::::: OO:'OO:::..OO:::. OO:::: OO::::.OO: OOOOOOOO:: OO::::.OO: OO:::: OO: OO.OO.OO:
 * OO:::: OO: OOOOOOOOO:::: OO:::: OO::::.OO: OO.. OO::: OO::::.OO: OO:::: OO: OO..OOOO:
 * :OO::::OO: OO.....OO:::: OO:::: OO::::.OO: OO::. OO:: OO::::.OO: OO:::: OO: OO:..OOO:
 * ::OOOOOO:: OO:::..OO:::: OO::::. OOOOOOO:: OO:::. OO:. OOOOOOO::. OOOOOOO:: OO::..OO:
 * :......:::..:::::..:::::..::::::.......:::..:::::..:::.......::::.......:::..::::..::
 * <p>
 * packageName    : io.baroka.entity
 * fileName       : Host
 * author         : darren
 * date           : 7/20/24
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 7/20/24        darren       최초 생성
 */
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Host {
    private String idx;

    private String title;

    private String username;

    private String serverHost;

    private Integer serverPort;

    private String password;

    private String pem;

    private String tunnelUsername;

    private String tunnelHost;

    private Integer tunnelPort;

    private Integer localPort;

    private String tunnelPassword;

    private String tunnelPem;
}
