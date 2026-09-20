import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 生成支付宝沙箱用的 RSA2(2048) 密钥对。
 *
 * <p>为什么需要这个：**「应用私钥」不会出现在支付宝控制台里，也永远不该出现** —— 私钥必须只存在于你自己的机器上，
 * 控制台只接受你上传的「应用公钥」，然后回给你「支付宝公钥」。很多人卡在"控制台找不到私钥"，是因为
 * 私钥本来就该由你本地生成。
 *
 * <p>为什么不用 openssl：生成的 PKCS#1（{@code BEGIN RSA PRIVATE KEY}）Java 用不了，还要再转 PKCS#8；
 * 本机也没装 openssl。Java 标准库直接产出 Java 与支付宝 SDK 都能吃的 PKCS#8。
 *
 * <p>用法（JDK 11+ 单文件运行，不需要编译）：
 * <pre>
 *   "%JAVA_HOME%\bin\java" KeyGen.java
 * </pre>
 * 私钥与公钥写到 {@code ~/.miaowang-alipay/}（**在代码库之外**，不会被误提交）；
 * 控制台要用的「应用公钥」会打印出来；**私钥只落盘、绝不打印**。
 */
public class KeyGen {

    public static void main(String[] args) throws Exception {
        // 默认落在当前工作目录下的 .alipay-sandbox。
        // 注意：必须是**工作区内**的路径 —— DSH 的文件沙箱只允许写工作区（写用户目录会被拒），
        // 而这个目录已经加进 .gitignore，所以不会被误提交。
        Path out = args.length > 0
                ? Paths.get(args[0])
                : Paths.get(System.getProperty("user.dir"), ".alipay-sandbox");
        Files.createDirectories(out);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        // 自检：先用私钥签一段数据、再用公钥验，确认这对密钥真的能用（省得到控制台才发现不对）
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update("miaowang-alipay-sandbox-selfcheck".getBytes("UTF-8"));
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update("miaowang-alipay-sandbox-selfcheck".getBytes("UTF-8"));
        boolean selfCheck = verifier.verify(signature);

        // 再确认重新加载一次也没问题（模拟服务端从配置里读密钥的场景）
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()));
        keyFactory.generatePublic(new X509EncodedKeySpec(keyPair.getPublic().getEncoded()));

        String privatePem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
        String publicOneLine = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        Path privateFile = out.resolve("app_private_key.pem");
        Path publicFile = out.resolve("app_public_key.pem");
        Path publicOneLineFile = out.resolve("app_public_key.oneline.txt");
        Files.write(privateFile, privatePem.getBytes("UTF-8"));
        Files.write(publicFile, publicPem.getBytes("UTF-8"));
        Files.write(publicOneLineFile, publicOneLine.getBytes("UTF-8"));
        Files.write(out.resolve("下一步怎么填.txt"), nextSteps().getBytes("UTF-8"));

        System.out.println("签名验签自检：" + (selfCheck ? "通过" : "失败（这对密钥有问题，别用）"));
        System.out.println("私钥文件：" + privateFile);
        System.out.println("公钥文件：" + publicFile);
        System.out.println();
        System.out.println("=== 应用公钥（PEM 形态）===");
        System.out.print(publicPem);
        System.out.println("=== 应用公钥（一行 base64，控制台一般就要这个）===");
        System.out.println(publicOneLine);
        System.out.println();
        System.out.println("说明：应用私钥只写进了 " + privateFile + " ，没有打印到任何输出里。");
        System.out.println("下一步照着 " + out.resolve("下一步怎么填.txt") + " 做。");
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static String nextSteps() {
        return String.join("\n",
                "支付宝沙箱密钥已经生成好了。顺序是这样：",
                "",
                "1) 打开支付宝开放平台沙箱控制台，找到「接口加签方式 / 密钥设置」，选【公钥】模式（不要选证书模式）。",
                "2) 把上面打印的「应用公钥」粘进去保存。控制台保存后会显示一个「支付宝公钥」，把它复制下来。",
                "   ⚠️ 别搞反：签名用这里的应用私钥，验签用控制台给的支付宝公钥。",
                "3) 在 Nacos（192.168.137.128:8848）里给 vip 服务配上：",
                "     vip.pay.alipay.appId             = 沙箱 APPID",
                "     vip.pay.alipay.merchantPrivateKey = app_private_key.pem 的内容（整段 PEM 或去掉头尾的一行 base64 都行）",
                "     vip.pay.alipay.alipayPublicKey    = 控制台给的支付宝公钥",
                "     vip.pay.alipay.gatewayUrl         = 控制台显示的沙箱网关地址",
                "     vip.pay.alipay.notifyUrl / returnUrl = 按后端代理给的清单填（returnUrl 要指到 http://localhost:5173/vip/pay/return）",
                "4) 重启 vip 服务，然后跑：node MiaoWang-frontproject/tools/probe-alipay.js",
                "   能拿到 payUrl 就说明签名通了；密钥格式有问题会给出可读的中文错误。",
                "",
                "安全提醒：app_private_key.pem 是你的私钥，不要发给任何人、不要提交到代码库、不要贴到聊天里。",
                "这个目录已经加进 .gitignore（它在工作区内，但不会被 git 跟踪），不要把它挪进会被提交的地方。");
    }
}