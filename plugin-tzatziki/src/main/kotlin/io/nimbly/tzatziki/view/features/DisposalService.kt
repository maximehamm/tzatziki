package io.nimbly.tzatziki.view.features

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class DisposalService : Disposable {

    override fun dispose() {
        //NA
    }

    companion object {
        fun getInstance(project: Project): DisposalService {
            return project.getService(DisposalService::class.java)
        }
    }
}