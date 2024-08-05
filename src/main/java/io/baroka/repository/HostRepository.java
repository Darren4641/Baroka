//package io.baroka.repository;
//
//import io.baroka.entity.Host;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.Optional;
//
///**
// * -------------------------------------------------------------------------------------
// * ::::::'OO::::'OOO::::'OO:::'OO:'OO::::'OO:'OOOOOOOO:::'OOOOOOO::'OO::::'OO:'OO....OO:
// * :::::: OO:::'OO OO:::. OO:'OO:: OO::::.OO: OO.....OO:'OO.....OO: OO:::: OO: OOO...OO:
// * :::::: OO::'OO:..OO:::. OOOO::: OO::::.OO: OO::::.OO: OO::::.OO: OO:::: OO: OOOO..OO:
// * :::::: OO:'OO:::..OO:::. OO:::: OO::::.OO: OOOOOOOO:: OO::::.OO: OO:::: OO: OO.OO.OO:
// * OO:::: OO: OOOOOOOOO:::: OO:::: OO::::.OO: OO.. OO::: OO::::.OO: OO:::: OO: OO..OOOO:
// * :OO::::OO: OO.....OO:::: OO:::: OO::::.OO: OO::. OO:: OO::::.OO: OO:::: OO: OO:..OOO:
// * ::OOOOOO:: OO:::..OO:::: OO::::. OOOOOOO:: OO:::. OO:. OOOOOOO::. OOOOOOO:: OO::..OO:
// * :......:::..:::::..:::::..::::::.......:::..:::::..:::.......::::.......:::..::::..::
// * <p>
// * packageName    : io.baroka.repository
// * fileName       : HostRepository
// * author         : darren
// * date           : 7/20/24
// * description    :
// * ===========================================================
// * DATE              AUTHOR             NOTE
// * -----------------------------------------------------------
// * 7/20/24        darren       최초 생성
// */
//public interface HostRepository extends JpaRepository<Host, Long> {
//    Optional<Host> findByUsernameAndServerHost(String username, String host);
//
//}
