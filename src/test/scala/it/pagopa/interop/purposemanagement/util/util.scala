package it.pagopa.interop.purposemanagement

import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, API_ROLE, INTERNAL_ROLE, M2M_ROLE, SECURITY_ROLE}

package object util {

  final val existingRoles: Seq[String] = Seq(ADMIN_ROLE, SECURITY_ROLE, API_ROLE, M2M_ROLE, INTERNAL_ROLE)

}
