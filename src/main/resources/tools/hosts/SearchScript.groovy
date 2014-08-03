import groovy.sql.Sql;
import groovy.sql.DataSet;


    def findDatabase = { schema_name, connection ->

        def sql = new Sql(connection)
        def result = []

        def dbTypeMatcher
        def dbTypeWhere = ""
        def dbTypeWhereParams = []

        if (schema_name) {
            // try to find the db_type_id from within the schema_name. Schema_names have the format of db_X_abcde, where X is the db_type_id
            dbTypeMatcher = schema_name =~ /^db_(\d+)_.*$/;
            if (dbTypeMatcher.size() == 1 && dbTypeMatcher[0].size() == 2) {
                dbTypeWhere = " WHERE h.db_type_id = ?"
                dbTypeWhereParams = [dbTypeMatcher[0][1].toInteger()]
            }

        }

        sql.eachRow("""\
            SELECT 
                d.id as db_type_id, 
                d.simple_name, 
                d.full_name,
                d.list_database_script, 
                d.jdbc_class_name, 
                h.id as host_id, 
                h.jdbc_url_template,
                h.default_database, 
                h.admin_username, 
                h.admin_password 
            FROM 
                db_types d 
                    INNER JOIN hosts h ON 
                        d.id = h.db_type_id
            """ + dbTypeWhere, dbTypeWhereParams) {
            def jdbc_url_template = it.jdbc_url_template
            def populatedUrl = jdbc_url_template.replace("#databaseName#", it.default_database)
            def jdbc_class_name = it.jdbc_class_name
            def simple_name = it.simple_name
            def full_name = it.full_name

            def db_type_id = it.db_type_id
            def host_id = it.host_id

            def schemaNameWhere = ""
            def schemaNameWhereParams = []

            if (schema_name) {
                if (it.simple_name == "MySQL") {
                    schemaNameWhere = " WHERE `Database` = ?"
                } else {
                    schemaNameWhere = " WHERE schema_name = ?"
                }
                schemaNameWhereParams = [schema_name]
            } else {
                if (it.simple_name == "MySQL") {
                    schemaNameWhere = " WHERE `Database` LIKE 'db_${db_type_id}_%'"
                } else {
                    schemaNameWhere = " WHERE schema_name LIKE 'db_${db_type_id}_%'"
                }

            }

            def hostConnection = Sql.newInstance(populatedUrl, it.admin_username, it.admin_password, it.jdbc_class_name)
            hostConnection.eachRow(it.list_database_script + schemaNameWhere, schemaNameWhereParams) {
                def name = it.getAt(0)
                def short_code_matcher = name =~ /^db_\d+_(.*)$/
                def short_code = short_code_matcher[0][1]
                populatedUrl = jdbc_url_template.replace("#databaseName#", name)

                result.add([
                    __UID__:name,
                    __NAME__:name,
                    db_type_id: db_type_id,
                    jdbc_class_name: jdbc_class_name,
                    simple_name: simple_name,
                    full_name: full_name,
                    jdbc_url: populatedUrl,
                    username: "user_" + db_type_id + "_" + short_code,
                    pw: db_type_id + "_" + short_code
                ])
            }
            hostConnection.close()
        }

        sql.close()

        return result

    }




def result = []
def schema_name = null

// The only query we support is on the schema_name
if (query != null && (query.get("left") instanceof String) && (query.get("left") == "__UID__" || query.get("left") == "__NAME__")) {
    schema_name = query.get("right")
}

switch ( objectClass ) {
    case "databases":
        result = findDatabase(schema_name, connection)
    break

    default:
    result
}
return result