package controllers.actions

import java.time.LocalDate

import com.google.inject.ImplementedBy
import javax.inject.Inject
import models.AccessType
import models.requests.{IdentifierRequest, OptionalDataRequest}
import pages.Page
import play.api.mvc.ActionTransformer
import services.RequestCreationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}


class DataSetupImpl(
                      srn: String,
                      startDate: LocalDate,
                      version: Int,
                      accessType: AccessType,
                      optionCurrentPage: Option[Page],
                      requestCreationService:RequestCreationService
                    )(implicit val executionContext: ExecutionContext)
  extends DataSetup {

  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    requestCreationService.retrieveAndCreateRequest(srn, startDate, version, accessType, optionCurrentPage)(request,implicitly, implicitly)
  }
}

class DataSetupActionImpl @Inject()(
                                      requestCreationService:RequestCreationService
                                    )(implicit val executionContext: ExecutionContext)
  extends DataSetupAction {
  override def apply(srn: String, startDate: LocalDate, optionVersion: Int, accessType: AccessType, optionCurrentPage: Option[Page]): DataSetup =
    new DataSetupImpl(srn, startDate, optionVersion, accessType, optionCurrentPage, requestCreationService)
}

@ImplementedBy(classOf[DataSetupImpl])
trait DataSetup extends ActionTransformer[IdentifierRequest, OptionalDataRequest]

@ImplementedBy(classOf[DataSetupActionImpl])
trait DataSetupAction {
  def apply(srn: String, startDate: LocalDate, version: Int, accessType: AccessType, optionCurrentPage: Option[Page]): DataSetup
}