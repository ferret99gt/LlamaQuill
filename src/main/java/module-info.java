module com.llamaquill
{
    requires javafx.controls;
    requires transitive javafx.graphics;

    requires java.net.http;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires org.json;

    exports com.llamaquill;
}
