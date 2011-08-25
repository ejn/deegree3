//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/deegree3/trunk/deegree-core/deegree-core-base/src/main/java/org/deegree/filter/sql/PropertyNameMapper.java $
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.sqldialect.filter;

import org.deegree.filter.FilterEvaluationException;
import org.deegree.filter.expression.ValueReference;

/**
 * Implementations provide {@link ValueReference} to table/column mappings for {@link AbstractWhereBuilder}
 * implementations.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: mschneider $
 * 
 * @version $Revision: 30964 $, $Date: 2011-05-30 14:34:29 +0200 (Mo, 30. Mai 2011) $
 */
public interface PropertyNameMapper {

    /**
     * Returns the {@link PropertyNameMapping} for the given {@link ValueReference}.
     * 
     * @param propName
     *            property name, can be <code>null</code> (indicates that the default geometry property of the root
     *            object is requested)
     * @param aliasManager
     *            manager for creating and tracking table aliases, never <code>null</code>
     * @return relational mapping, may be <code>null</code> (if no mapping is possible)
     * @throws FilterEvaluationException
     *             indicates that the {@link ValueReference} is invalid
     * @throws UnmappableException
     */
    public PropertyNameMapping getMapping( ValueReference propName, TableAliasManager aliasManager )
                            throws FilterEvaluationException, UnmappableException;
}