package io.baroka.entity;

import com.jcraft.jsch.Identity;
import jakarta.persistence.*;
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
@Entity
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Host {
    @Id
    @Column(name = "idx", length = 255)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "server_host", length = 255)
    private String serverHost;

    @Column(name = "server_port", length = 255)
    private Integer serverPort;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "pem", columnDefinition = "text")
    private String pem;

    @Column(name = "tunnel_username", length = 255, nullable = true)
    private String tunnelUsername;

    @Column(name = "tunnel_host", length = 255, nullable = true)
    private String tunnelHost;

    @Column(name = "tunnel_port", length = 255, nullable = true)
    private Integer tunnelPort;

    @Column(name = "local_port", length = 255, nullable = true)
    private Integer localPort;

    @Column(name = "tunnel_password", length = 255, nullable = true)
    private String tunnelPassword;

    @Column(name = "tunnel_pem", length = 255, nullable = true)
    private String tunnelPem;
}
