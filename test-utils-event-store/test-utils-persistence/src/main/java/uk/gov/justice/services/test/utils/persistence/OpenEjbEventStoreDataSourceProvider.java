package uk.gov.justice.services.test.utils.persistence;

import uk.gov.justice.services.jdbc.persistence.JdbcDataSourceProvider;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;

@ApplicationScoped
public class OpenEjbEventStoreDataSourceProvider implements JdbcDataSourceProvider {

    @Resource(name = "openejb/Resource/frameworkeventstore")
    private DataSource dataSource;

    @Override
    public DataSource getDataSource(final String jndiName) {
        return dataSource;
    }
}
