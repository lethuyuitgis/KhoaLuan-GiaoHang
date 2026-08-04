# LỜI MỞ ĐẦU

Trong khoảng một thập kỷ trở lại đây, thương mại điện tử và dịch vụ giao đồ ăn trực tuyến tại Việt Nam đã phát triển với tốc độ hết sức nhanh chóng. Theo báo cáo của Hiệp hội Thương mại điện tử Việt Nam, quy mô thị trường giao đồ ăn trực tuyến năm 2024 đã vượt mốc một tỷ đô la Mỹ với mức tăng trưởng kép duy trì ở hai con số trong nhiều năm liên tiếp [1]. Hai nền tảng tổng hợp là GrabFood và ShopeeFood chi phối phần lớn thị phần, trong khi các phương thức thanh toán điện tử như MoMo, ZaloPay và VNPay dần trở thành thói quen tiêu dùng phổ biến của người dân đô thị. Sự chuyển dịch này đã đặt nhu cầu giao hàng chặng cuối (last-mile delivery) vào vị trí trung tâm của toàn bộ chuỗi cung ứng bán lẻ trực tuyến.

Tuy nhiên, dòng chảy phát triển nói trên chủ yếu phục vụ tệp khách hàng đại trà của các nền tảng tổng hợp. Đối với các doanh nghiệp nhỏ và vừa — quán ăn gia đình, tiệm tạp hoá khu phố, cửa hàng bánh của hộ kinh doanh — bài toán lại hoàn toàn khác. Nhóm này thường đứng trước ba lựa chọn đều không trọn vẹn. Thứ nhất, tham gia nền tảng tổng hợp thì tiếp cận được lượng người dùng lớn nhưng phải chịu mức hoa hồng 20–25% trên mỗi đơn, đồng thời mất quyền sở hữu dữ liệu khách hàng và không kiểm soát được trải nghiệm thương hiệu. Thứ hai, tự xây dựng một ứng dụng riêng cho cả hai nền tảng di động cùng backend, hạ tầng bản đồ và cổng thanh toán lại đòi hỏi chi phí phát triển và vận hành rất cao, vượt khả năng đầu tư của một shop nhỏ. Thứ ba, tiếp tục vận hành thủ công qua Messenger, Zalo hoặc điện thoại thì dễ nhầm lẫn, thất lạc đơn, không có cơ chế theo dõi trạng thái đơn hàng hay vị trí giao hàng thời gian thực, và gần như không thể mở rộng khi lượng đơn tăng lên.

Trong bối cảnh đó, Telegram nổi lên như một nền tảng giàu tiềm năng nhưng chưa được khai thác đúng mức tại thị trường Việt Nam. Telegram hiện có khoảng một tỷ người dùng hoạt động hằng tháng trên toàn cầu và là một trong những ứng dụng có tốc độ tăng trưởng nhanh nhất khu vực Đông Nam Á [21]. Đặc biệt, nền tảng này cung cấp sẵn ba đặc tính kỹ thuật rất phù hợp với bài toán giao hàng quy mô nhỏ và vừa: Telegram Bot cho phép xây dựng tác tử hội thoại miễn phí với bàn phím thao tác một chạm; Telegram Mini App cho phép nhúng một ứng dụng web đầy đủ tính năng vào cửa sổ chat kèm cơ chế xác thực danh tính bằng chữ ký HMAC trên dữ liệu khởi tạo; và Telegram Live Location cho phép chia sẻ vị trí GPS liên tục do chính Telegram ký và phát đi, nhờ đó giảm đáng kể chi phí xây dựng mô-đun theo dõi vị trí riêng.

Xuất phát từ những phân tích trên, đề tài "Xây dựng hệ thống quản lý giao hàng tích hợp Telegram Mini App, Web Admin và VNPay" được lựa chọn nhằm cung cấp một giải pháp giao hàng đầu cuối chi phí thấp cho mô hình shop bán lẻ trực tuyến quy mô nhỏ và vừa, với sản lượng khoảng 50–500 đơn mỗi ngày và đội ngũ 1–10 shipper nội bộ. Hệ thống lấy Telegram làm cổng vào duy nhất cho hai vai trò khách hàng và shipper, kết hợp một trang quản trị trên trình duyệt (Web Admin) dành cho chủ shop. Toàn bộ chuỗi nghiệp vụ — từ việc khách đặt đơn trên Telegram Mini App, chủ shop gán đơn cho shipper, shipper chia sẻ vị trí trực tiếp, khách theo dõi bản đồ thời gian thực, thanh toán qua tiền mặt khi nhận hàng (COD) hoặc cổng VNPay, cho đến khi khách đánh giá shipper — được thiết kế để vận hành liền mạch trên ba kênh giao diện.

Báo cáo khóa luận được tổ chức thành bốn chương. Chương 1 phát biểu bài toán, trình bày ý nghĩa khoa học và thực tiễn, mục tiêu, đối tượng ứng dụng và phạm vi nghiên cứu, khảo sát các đề tài liên quan, đồng thời trình bày kế hoạch thực hiện và phân công công việc trong nhóm. Chương 2 trình bày cơ sở lý thuyết và các nền tảng công nghệ then chốt được sử dụng. Chương 3 tập trung vào phân tích và thiết kế hệ thống, bao gồm phân tích yêu cầu, kiến trúc tổng thể, thiết kế cơ sở dữ liệu, thiết kế giao diện lập trình ứng dụng, máy trạng thái và mô hình bảo mật. Chương 4 trình bày quá trình cài đặt, kiểm thử, đánh giá kết quả và đề xuất hướng phát triển. Nội dung chương 1 dưới đây sẽ làm rõ bài toán mà đề tài đặt ra cùng ý nghĩa và phạm vi giải quyết của nó.

# CHƯƠNG 1: PHÁT BIỂU BÀI TOÁN

Chương này trình bày một cách hệ thống bài toán mà đề tài hướng tới giải quyết, theo đúng trình tự đã đăng ký trong đề cương khóa luận. Trước hết, phần 1.1 phân tích ý nghĩa khoa học và ý nghĩa thực tiễn của đề tài nhằm khẳng định giá trị đóng góp cả về mặt học thuật lẫn ứng dụng. Phần 1.2 phát biểu bài toán cùng các yêu cầu cụ thể đặt ra cho hệ thống. Phần 1.3 trình bày mục tiêu cần đạt được cũng như đối tượng ứng dụng và phạm vi nghiên cứu được khoanh vùng rõ ràng. Phần 1.4 khảo sát các đề tài và giải pháp liên quan, chỉ ra hạn chế của chúng cùng hướng khắc phục mà đề tài lựa chọn. Phần 1.5 và 1.6 trình bày kế hoạch thực hiện và phân công công việc của nhóm. Cuối cùng, phần 1.7 tổng hợp các đóng góp kỹ thuật chính của đề tài cùng những chỉ số định lượng kèm theo.

## 1.1. Ý nghĩa khoa học và thực tiễn của đề tài

Đề tài mang trong mình cả ý nghĩa khoa học lẫn ý nghĩa thực tiễn, tương ứng với hai khía cạnh không thể tách rời của một khóa luận thuộc chuyên ngành công nghệ phần mềm: một mặt là việc vận dụng có chọn lọc các phương pháp và mẫu thiết kế hiện đại, mặt khác là khả năng ứng dụng trực tiếp cho một nhóm người dùng cụ thể trong thực tế.

**Về ý nghĩa khoa học**, đề tài không dừng lại ở việc xây dựng một sản phẩm chạy được mà chú trọng vận dụng một cách nhất quán các phương pháp và mẫu thiết kế phần mềm đã được cộng đồng thừa nhận rộng rãi.

Thứ nhất, hệ thống áp dụng kiến trúc Modular Monolith theo tinh thần của phương pháp thiết kế hướng miền (Domain-Driven Design) ở mức độ tinh gọn. Toàn bộ hệ thống được tổ chức thành tám ngữ cảnh nghiệp vụ tách bạch, có đồ thị phụ thuộc một chiều và không tồn tại chu trình. Các mô-đun không gọi trực tiếp vào tầng truy xuất dữ liệu của nhau mà giao tiếp thông qua cơ chế sự kiện ứng dụng của nền tảng, nhờ đó vừa giữ được ưu điểm dễ triển khai của kiến trúc nguyên khối, vừa tạo sẵn lối thoát để tách thành các dịch vụ nhỏ trong tương lai mà gần như không phải viết lại phần lõi nghiệp vụ [7], [14], [24].

Thứ hai, đề tài áp dụng máy trạng thái hữu hạn (Finite State Machine) cho vòng đời đơn hàng. Vòng đời này được mô hình hoá thành bảy trạng thái cùng một tập các chuyển dịch được phép, nhờ đó ngăn chặn ngay tại tầng nghiệp vụ mọi thao tác chuyển trạng thái không hợp lệ. Đây là cách tiếp cận có cơ sở lý thuyết vững chắc, giúp mã nguồn tường minh và dễ kiểm chứng hơn so với việc kiểm tra điều kiện rải rác.

Thứ ba, đề tài tuân thủ phương pháp phát triển hướng kiểm thử (Test-Driven Development). Toàn bộ logic nghiệp vụ then chốt — như ký và xác minh chữ ký thanh toán, chuyển trạng thái đơn hàng, tính cước theo khoảng cách và xử lý thông báo thanh toán bảo đảm tính bất biến khi lặp lại (idempotent) — đều được viết kiểm thử trước rồi mới hiện thực, theo chu kỳ Red–Green–Refactor [3]. Việc kiểm thử được thực hiện với cơ sở dữ liệu thật chạy trong container thay vì giả lập, qua đó nâng cao độ tin cậy của kết quả kiểm thử.

Thứ tư, hệ thống được xây dựng theo nguyên tắc bảo mật nhiều lớp (defense-in-depth), tức là không dựa duy nhất vào một cơ chế phòng thủ mà bố trí nhiều lớp kiểm soát song song và độc lập. Các lớp này trải dài từ xác thực danh tính bằng chữ ký HMAC, xác thực bằng chuỗi thông báo (token) có thời gian sống ngắn, phân quyền ở mức phương thức điều khiển, danh sách cho phép trên kênh truyền thông thời gian thực, cho đến chống tấn công thời gian (timing attack), chống phát lại (replay) và ghi vết kiểm toán các giao dịch thanh toán [17], [18]. Mỗi lớp đều được bảo đảm bằng ít nhất một kiểm thử hồi quy.

**Về ý nghĩa thực tiễn**, đề tài giải quyết một nhu cầu có thật và cấp thiết của phân khúc shop bán lẻ nhỏ và vừa, đúng vào khoảng trống mà các giải pháp hiện có trên thị trường chưa lấp đầy một cách hợp lý.

Trước hết là bài toán chi phí. Hệ thống của đề tài có thể vận hành ổn định trên một máy chủ ảo phổ thông với chi phí khoảng 10 đô la Mỹ mỗi tháng, thấp hơn nhiều so với mức 50–100 đô la Mỹ mỗi tháng khi shop tự xây dựng ứng dụng riêng, và không phải chịu khoản hoa hồng 20–25% mỗi đơn như khi tham gia các nền tảng tổng hợp. Đây là mức chi phí nằm trong khả năng chi trả của đại đa số hộ kinh doanh và doanh nghiệp nhỏ.

Kế đến là sự thuận tiện trong triển khai và sử dụng. Do lấy Telegram làm cổng vào, cả khách hàng lẫn shipper đều không cần cài đặt thêm bất kỳ ứng dụng mới nào mà chỉ sử dụng công cụ họ vốn dùng hằng ngày; chủ shop cũng chỉ cần một trình duyệt web thông thường. Điều này loại bỏ rào cản cài đặt ứng dụng cùng dung lượng lưu trữ đi kèm, vốn là trở ngại lớn đối với việc tiếp cận khách hàng của các giải pháp tự xây.

Sau cùng là quyền tự chủ dữ liệu. Toàn bộ dữ liệu khách hàng, đơn hàng và giao dịch đều thuộc quyền sở hữu và quản lý của chính shop, thay vì bị các nền tảng trung gian nắm giữ. Đây là yếu tố mang ý nghĩa chiến lược dài hạn, cho phép shop chủ động khai thác dữ liệu để chăm sóc khách hàng và xây dựng thương hiệu riêng.

## 1.2. Phát biểu bài toán

Bài toán mà đề tài đặt ra là quản lý hiệu quả và an toàn toàn bộ chuỗi giao hàng chặng cuối cho một shop bán lẻ trực tuyến — từ khâu đặt đơn, điều phối shipper, theo dõi vị trí, thanh toán cho đến đánh giá dịch vụ — trên ba vai trò người dùng (khách hàng, shipper, chủ shop) mà không buộc bên nào phải cài đặt thêm phần mềm ngoài công cụ họ đã dùng hằng ngày.

Từ phát biểu tổng quát đó, bài toán được cụ thể hoá thành bảy nhóm yêu cầu chức năng. Thứ nhất, quản lý sản phẩm (thêm, sửa, xoá, tìm kiếm) và quản lý đơn hàng. Thứ hai, khách hàng đặt đơn, chọn phương thức thanh toán (COD hoặc VNPay), theo dõi đơn và đánh giá shipper qua Telegram Mini App kết hợp Telegram Bot. Thứ ba, chủ shop xem đơn, gán đơn cho shipper, theo dõi trạng thái và nhận thông báo thời gian thực qua Web Admin. Thứ tư, shipper nhận hoặc từ chối đơn, cập nhật trạng thái giao hàng và chia sẻ vị trí trực tiếp qua Telegram Bot kết hợp Mini App. Thứ năm, trạng thái đơn hàng được cập nhật theo máy trạng thái hữu hạn bảy trạng thái, ngăn mọi chuyển dịch trái phép. Thứ sáu, thanh toán điện tử qua cổng VNPay ở môi trường thử nghiệm với thông báo IPN làm nguồn sự thật duy nhất. Thứ bảy, báo cáo và thống kê doanh thu cho chủ shop.

## 1.3. Mục tiêu và phạm vi của đề tài

### 1.3.1. Mục tiêu của đề tài

Mục tiêu tổng quát của đề tài là xây dựng một hệ thống quản lý giao hàng đầu cuối hoàn chỉnh cho mô hình shop bán lẻ trực tuyến quy mô nhỏ và vừa, với ba luồng nghiệp vụ cốt lõi vận hành liền mạch trên ba kênh giao diện tương ứng với ba vai trò người dùng. Khách hàng đặt đơn, theo dõi vị trí shipper và đánh giá dịch vụ thông qua Telegram Mini App kết hợp Telegram Bot. Shipper tiếp nhận đơn, cập nhật trạng thái giao hàng và chia sẻ vị trí thời gian thực thông qua Telegram Bot kết hợp Telegram Mini App. Chủ shop quản lý sản phẩm, đơn hàng, shipper, theo dõi báo cáo doanh thu và nhận thông báo thời gian thực thông qua Web Admin trên trình duyệt máy tính.

Từ mục tiêu tổng quát nói trên, đề tài cụ thể hoá thành các mục tiêu kỹ thuật có thể lượng hoá và kiểm chứng như sau.

Một là, hỗ trợ đặt đơn trực tuyến với hai phương thức thanh toán: tiền mặt khi nhận hàng (COD) và thanh toán điện tử qua cổng VNPay ở môi trường thử nghiệm (sandbox), áp dụng cơ chế ký HMAC-SHA512 và lấy thông báo thanh toán tức thời từ máy chủ (IPN) làm nguồn sự thật duy nhất để cập nhật trạng thái giao dịch.

Hai là, theo dõi vị trí shipper thời gian thực trên bản đồ thông qua Telegram Live Location, với độ trễ đầu-cuối dưới 3 giây tính từ thời điểm Telegram phát đi bản cập nhật vị trí cho đến khi khách hàng nhìn thấy điểm định vị di chuyển trên bản đồ.

Ba là, áp dụng máy trạng thái hữu hạn cho vòng đời đơn hàng với bảy trạng thái và danh sách chuyển dịch được phép, ngăn ngừa mọi chuyển dịch trái phép ngay tại tầng nghiệp vụ.

Bốn là, triển khai hệ thống thông báo thời gian thực dựa trên nền tảng truyền thông WebSocket kết hợp Telegram Bot, cho phép chủ shop nhìn thấy đơn mới ngay trên bảng điều khiển và shipper nhận được đề nghị giao đơn chỉ trong vòng vài trăm mili-giây sau khi sự kiện phát sinh.

Năm là, đóng gói toàn bộ hệ thống bằng Docker Compose để có thể triển khai bằng đúng ba lệnh, hỗ trợ cả môi trường phát triển và môi trường sản phẩm, đồng thời áp dụng cơ chế dừng-ngay-khi-lỗi đối với các biến môi trường nhạy cảm nhằm tránh triển khai nhầm bằng thông tin cấu hình thử nghiệm.

Bên cạnh các mục tiêu kỹ thuật, đề tài còn hướng tới một mục tiêu về mặt phương pháp luận, đó là minh hoạ một quy trình phát triển phần mềm có kỷ luật, đi qua đầy đủ các bước nghiên cứu, lập kế hoạch, kiểm tra kế hoạch, thực thi và đánh giá mã, đồng thời duy trì trạng thái build luôn thành công sau mỗi lần chuyển giao mã nguồn.

### 1.3.2. Đối tượng ứng dụng và phạm vi nghiên cứu của đề tài

**Đối tượng ứng dụng.** Đề tài hướng tới bài toán quản lý giao hàng chặng cuối cho mô hình bán lẻ trực tuyến quy mô nhỏ và vừa, tiêu biểu là các shop kinh doanh đồ ăn, đồ uống (F&B) và tạp hoá. Nhóm đối tượng này có một số đặc trưng chung: sản lượng khoảng 50–500 đơn mỗi ngày, đội ngũ giao hàng nội bộ gồm 1–10 shipper, và bán kính giao hàng dưới 10 km tính từ điểm xuất phát. Trong mô hình đó có ba bên liên quan trực tiếp, mỗi bên sử dụng thiết bị và có yêu cầu trải nghiệm khác nhau: khách hàng đầu cuối, shipper và chủ shop.

Điểm mà đề tài đặc biệt quan tâm là bài toán phối hợp đa kênh, tức là làm sao để ba vai trò cùng tham gia vào một quy trình nghiệp vụ thống nhất mà không buộc bên nào phải cài đặt thêm phần mềm ngoài công cụ họ vốn dùng hằng ngày. Đây chính là hướng tiếp cận khác biệt so với các nền tảng tổng hợp vốn đòi hỏi ba ứng dụng riêng biệt cho ba vai trò.

**Phạm vi nghiên cứu.** Phạm vi của đề tài được xác định theo phương pháp MoSCoW, nghĩa là phân loại các yêu cầu theo bốn mức độ ưu tiên: bắt buộc phải có (Must have), nên có (Should have), có thể có (Could have) và không thực hiện trong phạm vi này (Won't have). Cách phân loại này giúp xác định rạch ròi ranh giới giữa phần cốt lõi bắt buộc và phần bổ sung, đồng thời tránh hiện tượng phình phạm vi (scope creep) trong quá trình thực hiện. Bảng 1.2 liệt kê đầy đủ mười lăm yêu cầu đã được phân loại, gồm tám yêu cầu Must, ba yêu cầu Should, hai yêu cầu Could và hai yêu cầu Won't, kèm theo trạng thái hoàn thành của từng yêu cầu tại thời điểm bảo vệ.

**Bảng 1.2. Danh sách yêu cầu theo phương pháp MoSCoW (Must / Should / Could / Won't)**

| STT | Tính năng | Ưu tiên | Trạng thái |
|---|---|---|---|
| 1 | Khách đặt đơn qua Telegram Mini App | Must | Đã hoàn thành |
| 2 | Chủ shop xem và gán đơn cho shipper qua Web Admin | Must | Đã hoàn thành |
| 3 | Shipper nhận / từ chối đơn qua Telegram Bot | Must | Đã hoàn thành |
| 4 | Cập nhật trạng thái đơn theo máy trạng thái hữu hạn | Must | Đã hoàn thành |
| 5 | Telegram Live Location khi đang giao | Must | Đã hoàn thành |
| 6 | Thông báo thời gian thực qua Bot và WebSocket | Must | Đã hoàn thành |
| 7 | Lịch sử đơn hàng (khách hàng và chủ shop) | Must | Đã hoàn thành |
| 8 | Thanh toán điện tử qua VNPay sandbox | Must | Đã hoàn thành |
| 9 | Tính cước theo khoảng cách (công thức Haversine) | Should | Đã hoàn thành |
| 10 | Đánh giá shipper 1–5 sao sau khi giao | Should | Đã hoàn thành |
| 11 | Báo cáo và biểu đồ cho chủ shop (Recharts) | Should | Đã hoàn thành |
| 12 | Lưu địa chỉ thường dùng của khách | Could | Chưa triển khai (hướng phát triển) |
| 13 | Chat trong Bot giữa khách và shipper | Could | Chưa triển khai (hướng phát triển) |
| 14 | Đa shop / đa người thuê (multi-tenant) | Won't | Ngoài phạm vi |
| 15 | Ứng dụng quản trị siêu admin cho nhiều shop | Won't | Ngoài phạm vi |

Toàn bộ mười một yêu cầu thuộc hai nhóm Must và Should (tám Must, ba Should) đã được hiện thực và kiểm thử đầy đủ; hai yêu cầu Could được xếp vào hướng phát triển trình bày ở Chương 4; hai yêu cầu Won't nằm ngoài phạm vi đề tài. Nội dung cụ thể của từng nhóm như sau.

Nhóm yêu cầu bắt buộc phải có (Must have) bao gồm: khách đặt đơn qua Telegram Mini App; chủ shop xem và gán đơn cho shipper qua Web Admin; shipper nhận hoặc từ chối đơn qua Telegram Bot; cập nhật trạng thái đơn theo máy trạng thái hữu hạn; chia sẻ vị trí trực tiếp qua Telegram Live Location khi đang giao; thông báo thời gian thực qua Bot và WebSocket; lịch sử đơn hàng cho khách hàng và chủ shop; cùng thanh toán điện tử qua cổng VNPay.

Nhóm yêu cầu nên có (Should have) bao gồm: tính cước theo khoảng cách bằng công thức Haversine; đánh giá shipper theo thang 1–5 sao sau khi giao xong; và báo cáo, biểu đồ thống kê dành cho chủ shop.

Nhóm yêu cầu có thể có (Could have) — được xếp vào hướng phát triển trong tương lai — gồm việc lưu địa chỉ thường dùng của khách và tính năng trò chuyện trực tiếp giữa khách và shipper trong Bot.

Nhóm yêu cầu không thực hiện (Won't have), tức nằm ngoài phạm vi đề tài, gồm: hỗ trợ đa shop hoặc đa người thuê; và ứng dụng quản trị cấp cao cho nhiều shop.

Ngoài phân loại theo MoSCoW, phạm vi đề tài còn được khoanh vùng bằng một số giới hạn cụ thể nhằm tập trung nguồn lực vào phần cốt lõi. Hệ thống chỉ phục vụ một shop duy nhất, không hỗ trợ nhiều cửa hàng cùng vận hành trên một backend. Hệ thống chỉ gồm ba vai trò là khách hàng, shipper và chủ shop, không có vai trò siêu quản trị viên hệ thống. Về thanh toán điện tử, đề tài chỉ tích hợp một cổng duy nhất là VNPay ở môi trường thử nghiệm; việc mở rộng sang các cổng khác như MoMo hay ZaloPay được xem là hướng phát triển tương lai. Đề tài không tích hợp với các đối tác giao vận bên ngoài như Giao Hàng Nhanh, Giao Hàng Tiết Kiệm hay Ahamove, bởi toàn bộ shipper đều là nhân sự nội bộ của shop. Cuối cùng, về hạ tầng bản đồ, hệ thống sử dụng dữ liệu bản đồ mở OpenStreetMap kết hợp thư viện react-leaflet nhằm tránh phụ thuộc vào các dịch vụ bản đồ trả phí và khoá truy cập (API key) đi kèm.

## 1.4. Các đề tài liên quan

Trước khi xác lập hướng tiếp cận riêng, nhóm khảo sát hai nhóm giải pháp hiện có cho bài toán giao hàng của shop bán lẻ trực tuyến: các nền tảng giao đồ ăn tổng hợp (tiêu biểu là GrabFood và ShopeeFood) và các phần mềm quản lý giao hàng do shop tự xây dựng hoặc thuê phát triển.

Về ưu điểm, các nền tảng tổng hợp sở hữu lượng người dùng rất lớn, cung cấp sẵn theo dõi vị trí GPS thời gian thực và cổng thanh toán đầy đủ; trong khi đó, giải pháp shop tự xây cho phép shop tự chủ hoàn toàn về dữ liệu và quy trình vận hành.

Tuy nhiên, mỗi nhóm giải pháp đều bộc lộ hạn chế rõ rệt đối với phân khúc shop nhỏ và vừa. Nền tảng tổng hợp thu hoa hồng cao (20–25% mỗi đơn), shop mất quyền sở hữu dữ liệu khách hàng, không kiểm soát được trải nghiệm thương hiệu, còn khách buộc phải cài một ứng dụng riêng dung lượng khoảng 150 MB. Giải pháp tự xây ứng dụng native cho cả iOS và Android đòi hỏi chi phí phát triển và vận hành cao (50–100 đô la Mỹ mỗi tháng), vượt khả năng đầu tư của shop nhỏ. Phương án còn lại — vận hành thủ công qua Messenger hoặc Zalo — không có theo dõi trạng thái đơn, không có vị trí giao hàng thời gian thực, dễ thất lạc đơn và không thể mở rộng.

Để làm rõ vị trí của hệ thống trong bức tranh chung, Bảng 1.1 đối chiếu hệ thống của đề tài với hai nhóm giải pháp phổ biến nhất tại thị trường Việt Nam trên tám tiêu chí, trải từ trải nghiệm người dùng (yêu cầu cài đặt ứng dụng, theo dõi GPS), chi phí vận hành, quyền tự chủ dữ liệu cho đến đặc điểm kiến trúc và mức độ phụ thuộc bên thứ ba.

**Bảng 1.1. So sánh hệ thống của đề tài với các giải pháp giao hàng hiện có**

| Tiêu chí | Hệ thống của đề tài | GrabFood / ShopeeFood | Shop tự xây ứng dụng riêng |
|---|---|---|---|
| Khách hàng cần cài ứng dụng mới | Không (chỉ dùng Telegram đã có) | Có (~150 MB) | Có |
| Theo dõi GPS thời gian thực | Có, qua Telegram Live Location | Có | Hiếm khi có; thường chỉ link web |
| Chi phí triển khai hằng tháng | ~10 USD (1 VPS phổ thông) | Không áp dụng (mô hình thương mại) | 50–100 USD (PaaS + dịch vụ bản đồ) |
| Tự quản lý hạ tầng & dữ liệu | Có | Không | Có |
| Tích hợp cổng thanh toán nội địa | VNPay (mở rộng được) | Đầy đủ | Tuỳ doanh nghiệp |
| Web Admin riêng cho chủ shop | Có | Có | Hiếm |
| Kiến trúc dễ mở rộng | Modular Monolith (DDD-lite) | Microservices nội bộ | Thường monolith thuần |
| Phụ thuộc bên thứ ba | Telegram, VNPay | Toàn bộ chuỗi vận hành | Cổng thanh toán, bản đồ, lưu trữ |

Kết quả so sánh cho thấy hệ thống của đề tài lấp đúng khoảng trống định vị giữa hai nhóm giải pháp: chi phí triển khai thấp tương đương phương án tự xây, trải nghiệm theo dõi vị trí gần với các nền tảng tổng hợp, trong khi chủ shop vẫn tự chủ hoàn toàn về dữ liệu khách hàng. Hình 1.1 và Hình 1.2 minh hoạ hai kênh giao diện tiêu biểu của hệ thống đã hoàn thành: màn hình danh mục sản phẩm trên Telegram Mini App dành cho khách hàng và bảng điều khiển tổng quan trên Web Admin dành cho chủ shop.

![Hình 1.1. Telegram Mini App của khách hàng — màn hình danh mục sản phẩm](screenshots/miniapp-cust-01-catalog.png){width=7cm}

![Hình 1.2. Web Admin của chủ shop — bảng điều khiển tổng quan](screenshots/admin-02-dashboard.png){width=13cm}

Từ khảo sát trên, đề tài lựa chọn hướng khắc phục cụ thể cho từng hạn chế. Một là chi phí thấp và tự chủ dữ liệu: không chịu hoa hồng nền tảng, chủ shop sở hữu toàn bộ dữ liệu khách, chi phí vận hành khoảng 10 đô la Mỹ mỗi tháng. Hai là không yêu cầu cài ứng dụng mới: khách và shipper dùng Telegram sẵn có, chủ shop dùng trình duyệt. Ba là theo dõi GPS thời gian thực với chi phí thấp nhờ tận dụng Telegram Live Location thay vì tự lập trình truyền phát GPS. Bốn là thanh toán điện tử an toàn theo đúng mẫu IPN làm nguồn sự thật của VNPay, tránh các lỗi phổ biến như tin tưởng Return URL, tấn công thời gian hay phát lại thông báo. Năm là bảo mật nhiều lớp từ xác thực HMAC initData, JWT, phân quyền mức phương thức, danh sách cho phép trên kênh WebSocket đến ghi vết kiểm toán giao dịch [17], [18]. Sáu là kiến trúc Modular Monolith dễ mở rộng, tạo sẵn lối thoát chuyển sang microservices mà không phải viết lại nghiệp vụ.

## 1.5. Kế hoạch thực hiện

Đề tài được thực hiện trong mười hai tuần theo kế hoạch đã đăng ký trong đề cương, đi tuần tự từ phân tích yêu cầu, thiết kế hệ thống đến cài đặt theo từng cụm chức năng, kiểm thử và đóng gói.

| Thời gian | Công việc | Mô tả chi tiết |
|---|---|---|
| Tuần 1–2 | Phân tích yêu cầu | Khảo sát hiện trạng giao hàng shop nhỏ; phân tích yêu cầu 3 vai trò; xác định phạm vi MoSCoW |
| Tuần 3–4 | Thiết kế hệ thống | Thiết kế kiến trúc Modular Monolith; thiết kế CSDL PostgreSQL + Flyway; thiết kế API và máy trạng thái đơn hàng; thiết kế giao diện 3 kênh |
| Tuần 5–6 | Nền tảng + xác thực | Thiết lập Maven/pnpm workspace; xác thực JWT (Web Admin) và Telegram initData (Mini App); CRUD sản phẩm và đơn hàng |
| Tuần 7 | Mini App khách + Bot | Mini App duyệt món, giỏ hàng, checkout COD; lệnh Bot /start, đăng ký và duyệt shipper |
| Tuần 8–9 | Giao hàng + theo dõi vị trí | Gán đơn, shipper nhận/từ chối; Telegram Live Location; bản đồ tracking realtime qua WebSocket |
| Tuần 10 | Thanh toán + báo cáo | Tích hợp VNPay sandbox (IPN làm nguồn sự thật); dashboard KPI và biểu đồ; đánh giá shipper |
| Tuần 11 | Kiểm thử và sửa lỗi | Viết unit/integration test (JUnit 5 + Testcontainers); kiểm thử tích hợp end-to-end; sửa lỗi |
| Tuần 12 | Đóng gói và hoàn thiện | Đóng gói Docker Compose + seed dữ liệu demo; viết tài liệu; chuẩn bị báo cáo và bảo vệ khóa luận |

## 1.6. Phân công công việc

Đề tài do nhóm ba sinh viên thực hiện, phân công theo thế mạnh của từng thành viên và bám sát bảng phân công đã đăng ký trong đề cương.

| Thành viên | Công việc chính |
|---|---|
| Lê Thị Trần Thủy | Thiết kế kiến trúc Modular Monolith và CSDL PostgreSQL + Flyway; lập trình Backend và Business Layer (Spring Boot, máy trạng thái đơn hàng); tích hợp Telegram (Bot, Mini App, Live Location) và cổng thanh toán VNPay; viết báo cáo |
| Ngô Phúc Hiếu | Lập trình Frontend Web Admin (React + Tailwind): quản lý sản phẩm, đơn hàng, Dashboard KPI và biểu đồ; kiểm thử (TDD) và đóng gói Docker Compose; bảo mật defense-in-depth; viết báo cáo |
| Nguyễn Thế Thưởng | Lập trình Frontend Telegram Mini App (khách + shipper); module giỏ hàng, checkout và bản đồ tracking (react-leaflet); thiết kế giao diện và chuẩn bị presentation; tổng hợp, viết tài liệu báo cáo |

## 1.7. Đóng góp của đề tài

Khép lại chương mở đầu, phần này tổng hợp tám đóng góp kỹ thuật chính của đề tài cùng các chỉ số định lượng kèm theo, làm cơ sở đối chiếu cho các chương phân tích, thiết kế và trình bày kết quả tiếp sau.

Thứ nhất, đề tài đề xuất và hiện thực mô hình triển khai lai (hybrid) trên nền tảng Telegram, trong đó ba kênh của Telegram được sử dụng đồng thời và mỗi kênh được phân cho một vai trò phù hợp với thiết bị và bối cảnh sử dụng của vai trò đó. Telegram Mini App (React 18, Vite 5) phục vụ khách hàng duyệt sản phẩm, đặt đơn và theo dõi bản đồ; Telegram Bot (xây dựng trên thư viện telegrambots-spring-boot-starter phiên bản 6.9.7.1) phục vụ shipper nhận đề nghị giao đơn và thao tác một chạm qua bàn phím nội tuyến, đồng thời cho phép khách hàng đánh giá sau khi nhận hàng; Web Admin (React, Tailwind CSS) phục vụ chủ shop làm việc trên màn hình máy tính. Khác với các nền tảng thương mại vốn ép mọi vai trò vào một ứng dụng di động duy nhất, cách phân vai theo thiết bị này tối ưu trải nghiệm của từng nhóm người dùng.

Thứ hai, đề tài hiện thực theo dõi vị trí thời gian thực bằng Telegram Live Location — hạng mục vốn tốn kém nhất khi xây dựng ứng dụng giao hàng — mà không cần lập trình truyền phát GPS ở phía thiết bị. Shipper chỉ cần chia sẻ vị trí trực tiếp ngay trong Telegram (15 phút, 1 giờ hoặc 8 giờ); Telegram gửi bản tin vị trí ban đầu rồi các bản tin cập nhật mỗi 5–10 giây tới webhook của bot. Ở phía backend, bộ xử lý LiveLocationHandler tiếp nhận bản tin, lưu vào bảng location_ping và phát sự kiện nội bộ; thành phần LocationBroadcaster lắng nghe sự kiện sau khi giao dịch cơ sở dữ liệu được xác nhận (AFTER_COMMIT) rồi đẩy toạ độ qua kênh STOMP tới trình duyệt của khách hàng. Cách tiếp cận này tiết kiệm ước tính 80% công sức so với tự lập trình truyền phát GPS, an toàn hơn vì dữ liệu vị trí đã được Telegram ký HMAC, đồng thời tiết kiệm pin cho thiết bị của shipper.

Thứ ba, đề tài áp dụng kiến trúc Modular Monolith với tám ngữ cảnh nghiệp vụ (bounded context) — shared, auth, order, delivery, payment, bot, notification và app — được tổ chức theo đồ thị phụ thuộc một chiều không có chu trình. Các mô-đun giao tiếp qua cơ chế sự kiện ứng dụng của Spring với ràng buộc chỉ phát sự kiện sau khi giao dịch được xác nhận, nhờ đó mô-đun phát không cần biết cấu trúc bên trong của mô-đun nhận, và hệ thống có thể tách thành các dịch vụ nhỏ về sau mà gần như không phải sửa mã nghiệp vụ. Kiến trúc này vừa giữ ưu điểm triển khai đơn giản của khối nguyên khối, vừa cô lập trách nhiệm theo nguyên tắc thiết kế hướng miền.

Thứ tư, đề tài tích hợp cổng thanh toán VNPay ở môi trường thử nghiệm theo đúng mẫu thiết kế lấy thông báo IPN làm nguồn sự thật duy nhất, thay vì cách làm sai phổ biến là tin tưởng Return URL. Điểm tạo giao dịch xác thực người dùng bằng Telegram initData, khởi tạo bản ghi thanh toán ở trạng thái chờ và ký URL bằng HMAC-SHA512; điểm Return URL chỉ xác minh chữ ký và hiển thị kết quả cho người dùng, tuyệt đối không cập nhật cơ sở dữ liệu; điểm IPN (máy chủ gọi máy chủ) xác minh chữ ký bằng phép so sánh thời gian không đổi để chống tấn công thời gian, kiểm tra khớp số tiền, bảo đảm tính bất biến khi lặp lại rồi mới cập nhật trạng thái giao dịch. Mọi sự kiện IPN, kể cả trường hợp chữ ký không hợp lệ, đều được lưu nguyên văn vào bảng kiểm toán payment_transaction phục vụ truy vết; một bộ lập lịch chạy mỗi 60 giây tự động chuyển các thanh toán chờ quá 15 phút sang trạng thái thất bại để giải phóng đơn hàng.

Thứ năm, đề tài xây dựng máy trạng thái hữu hạn cho hội thoại đánh giá shipper trong Telegram Bot. Sau khi đơn được giao thành công, bot gửi bàn phím nội tuyến năm sao; lựa chọn của khách được lưu vào bảng rating, còn điểm trung bình của shipper được tính lại bằng truy vấn tổng hợp thay vì cộng dồn để tránh sai số tích luỹ của số thực. Bot sau đó mời khách nhập nhận xét tuỳ chọn: dịch vụ quản lý trạng thái hội thoại chuyển người dùng sang trạng thái chờ nhận xét và lưu ngữ cảnh dưới dạng JSONB trong bảng conversation_state; khi khách gõ nội dung hoặc lệnh bỏ qua, máy trạng thái tự kết thúc. Đây là hạ tầng có thể tái sử dụng cho các luồng hội thoại nhiều bước khác.

Thứ sáu, đề tài triển khai bảo mật nhiều lớp với mười một lớp phòng thủ song song và độc lập: xác minh chữ ký HMAC trên Telegram initData chống giả mạo danh tính; JWT với thời gian sống 15 phút chống chiếm phiên; danh sách cho phép trong chuỗi bộ lọc bảo mật; phân quyền ở mức phương thức điều khiển chống leo thang đặc quyền; cơ chế phân giải người dùng hiện hành chống giả mạo mã khách hàng trên đường dẫn; xác thực kép tại thời điểm kết nối WebSocket chống kết nối nặc danh; danh sách cho phép khi đăng ký kênh WebSocket chống rò rỉ dữ liệu chéo người dùng; so sánh chữ ký VNPay theo thời gian không đổi chống tấn công thời gian; danh sách trắng tham số nhóm dữ liệu trước khi đưa vào truy vấn thống kê chống chèn SQL; ràng buộc duy nhất trên mã giao dịch cùng bảng kiểm toán chống phát lại thông báo thanh toán; và chỉ mục duy nhất một phần trên phân công giao hàng chống gán nhầm vị trí giữa các shipper. Mỗi lớp đều được bảo đảm bằng ít nhất một kiểm thử hồi quy.

Thứ bảy, đề tài minh hoạ một quy trình phát triển phần mềm có kỷ luật: toàn bộ mười pha phát triển đều đi qua đầy đủ các bước nghiên cứu, lập kế hoạch, kiểm tra kế hoạch, thực thi, đánh giá mã và sửa lỗi, sinh ra khoảng 33 000 dòng tài liệu kế hoạch, 6 500 dòng tài liệu nghiên cứu, chín báo cáo đánh giá mã và hơn 152 lần chuyển giao mã nguồn nguyên tử với trạng thái build luôn thành công. Quy trình này giúp phát hiện lỗi sớm: mười hai vấn đề được chặn ngay ở khâu kiểm tra kế hoạch trước khi viết mã, và năm lỗi nghiêm trọng — trong đó có khe hở phân quyền trên kênh STOMP và lỗi gán sai shipper cho bản tin vị trí — được phát hiện ở khâu đánh giá mã trước khi đưa vào vận hành.

Thứ tám, hệ thống đạt mức sẵn sàng triển khai chỉ bằng ba lệnh: tải mã nguồn, sao chép tệp cấu hình mẫu và điền bốn giá trị bí mật, rồi khởi động bằng Docker Compose. Toàn bộ hệ thống gồm năm container — cơ sở dữ liệu PostgreSQL 16, backend Spring Boot (build nhiều tầng, chạy với người dùng không đặc quyền, có điểm kiểm tra sức khoẻ), hai giao diện Mini App và Web Admin phục vụ qua nginx, cùng một reverse proxy — dựng xong trong khoảng ba phút ở lần đầu. Cấu hình môi trường sản phẩm áp dụng nguyên tắc dừng ngay khi thiếu biến môi trường nhạy cảm nhằm tránh triển khai nhầm bằng thông tin thử nghiệm, và bộ dữ liệu mẫu giúp bảng điều khiển có dữ liệu ngay sau khi khởi động.

Bên cạnh tám đóng góp định tính nói trên, Bảng 1.3 tổng hợp các chỉ số định lượng then chốt rút ra từ kho mã nguồn và tài liệu thiết kế tại thời điểm bảo vệ.

**Bảng 1.3. Các chỉ số định lượng đáng chú ý của đề tài**

| Chỉ số | Giá trị |
|---|---|
| Tổng số commit nguyên tử | hơn 152 |
| Tổng số dòng mã (Java + TypeScript) | ~21 000 |
| Tổng số dòng tài liệu kế hoạch, nghiên cứu, đánh giá mã | ~42 000 |
| Số kiểm thử backend (unit + integration) | 253 (231 unit + 22 integration) |
| Số kiểm thử frontend | 21 |
| Tổng số kiểm thử tự động | 274 |
| Tỷ lệ build thành công trên mỗi commit | 100% |
| Số Flyway migration | 17 (V1 đến V17) |
| Số ngữ cảnh nghiệp vụ (bounded context) | 8 |
| Số endpoint REST | 39 |
| Số kênh WebSocket | 2 (vị trí giao hàng theo đơn; đơn mới cho quản trị) |
| Số sự kiện liên mô-đun | 9 |
| Số trang Mini App | 9 (3 cho khách, 2 cho shipper, 4 chung) |
| Số trang Web Admin | 8 |
| Số Dockerfile | 3 (backend, miniapp, webadmin) |
| Số container trong Docker Compose | 5 |

Các con số trên phản ánh hai đặc trưng của quá trình thực hiện. Một là mật độ kiểm thử cao: 253 kiểm thử backend cùng 21 kiểm thử frontend (tổng cộng 274) trên khoảng 21 000 dòng mã, tương đương xấp xỉ 1,2 kiểm thử trên mỗi 100 dòng mã — vượt mức trung bình của các dự án mã nguồn mở cùng quy mô. Hai là mật độ tài liệu thiết kế lớn: khoảng 42 000 dòng tài liệu cho 21 000 dòng mã, tỉ lệ hai trên một, thể hiện kỷ luật thiết kế trước khi viết mã được duy trì xuyên suốt.

Như vậy, Chương 1 đã phát biểu bài toán giao hàng chặng cuối cho shop bán lẻ quy mô nhỏ và vừa, phân tích ý nghĩa khoa học và thực tiễn, xác định mục tiêu cùng phạm vi theo phương pháp MoSCoW, khảo sát các giải pháp liên quan và tổng hợp tám đóng góp kỹ thuật chính của đề tài. Trên nền tảng đó, Chương 2 sẽ đi sâu phân tích hệ thống với các yêu cầu chức năng và phi chức năng cụ thể; Chương 3 trình bày thiết kế kiến trúc, cơ sở dữ liệu, giao diện lập trình ứng dụng và mô hình bảo mật; Chương 4 trình bày kết quả sản phẩm, quá trình kiểm thử, đánh giá và hướng phát triển.
