import java.sql.*;

public class UserCheck {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://192.168.137.128:3306/MiaoWang?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        Connection conn = DriverManager.getConnection(url, "root", "123456");
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet cols = meta.getColumns(null, null, "miaowang_user", "del_flag");
        System.out.println("del_flag 列存在: " + cols.next());
        cols.close();
        cols = meta.getColumns(null, null, "miaowang_user", "%");
        while (cols.next()) {
            System.out.println("  列: " + cols.getString("COLUMN_NAME") + " (" + cols.getString("TYPE_NAME") + ", default=" + cols.getString("COLUMN_DEF") + ")");
        }
        cols.close();
        ResultSet rs = conn.createStatement().executeQuery(
            "SELECT username, password, del_flag FROM miaowang_user WHERE username = 'MapleLeaf'");
        while (rs.next()) {
            System.out.println("username=" + rs.getString("username") + ", password=" + rs.getString("password") + ", del_flag=" + rs.getInt("del_flag"));
        }
        rs.close();
        System.out.println("--- 全部用户的 del_flag 分布 ---");
        rs = conn.createStatement().executeQuery("SELECT del_flag, COUNT(*) c FROM miaowang_user GROUP BY del_flag");
        while (rs.next()) {
            System.out.println("del_flag=" + rs.getInt("del_flag") + " 数量=" + rs.getInt("c"));
        }
        rs.close();
        conn.close();
    }
}