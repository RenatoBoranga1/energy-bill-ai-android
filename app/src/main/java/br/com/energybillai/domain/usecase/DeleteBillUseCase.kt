package br.com.energybillai.domain.usecase

import br.com.energybillai.core.common.AppResult
import br.com.energybillai.domain.repository.BillRepository
import javax.inject.Inject

class DeleteBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
) {
    suspend operator fun invoke(billId: String): AppResult<Unit> = billRepository.deleteBill(billId)
}
