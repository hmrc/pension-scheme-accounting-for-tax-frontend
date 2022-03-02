package fileUploadParsers

import play.api.Logger


object TimeLogger {

  private val logger = Logger("FileUploadLogger")

  def logOperationTime[T](f: => T, description: String): T = {
    val start = System.currentTimeMillis
    val call = f
    val totalTime = System.currentTimeMillis() - start
    logger.warn(s"FileUpload logging $description is $totalTime ms")
    call
  }

}
