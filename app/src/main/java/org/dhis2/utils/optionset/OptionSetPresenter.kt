package org.dhis2.utils.optionset

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import io.reactivex.disposables.CompositeDisposable
import org.dhis2.data.forms.dataentry.fields.spinner.SpinnerViewModel
import org.dhis2.data.schedulers.SchedulerProvider
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.helpers.UidsHelper
import org.hisp.dhis.android.core.common.ObjectWithUid
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import org.dhis2.data.forms.dataentry.tablefields.spinner.SpinnerViewModel as TableSpinnerViewModel

class OptionSetPresenter(
        val view: OptionSetView,
        val d2: D2,
        val schedulerProvider: SchedulerProvider) {

    var disposable: CompositeDisposable = CompositeDisposable()
    private var optionsToHide: List<String>? = null
    private var optionGroupsToHide: List<String>? = null
    private var optionGroupsToShow: List<String>? = null
    private lateinit var optionSetUid: String

    fun init(optionSet: SpinnerViewModel) {
        this.optionSetUid = optionSet.optionSet()
        optionsToHide = if (optionSet.optionsToHide != null) optionSet.optionsToHide else ArrayList()
        optionGroupsToHide = if (optionSet.optionGroupsToHide != null) optionSet.optionGroupsToHide else ArrayList()
        optionGroupsToShow = if (optionSet.optionGroupsToShow != null) optionSet.optionGroupsToShow else ArrayList()
        getOptions()
    }

    fun init(optionSetTable: TableSpinnerViewModel) {
        this.optionSetUid = optionSetTable.optionSet()
        optionsToHide = if (optionSetTable.optionsToHide != null) optionSetTable.optionsToHide else ArrayList()
        optionGroupsToHide = if (optionSetTable.optionGroupsToHide != null) optionSetTable.optionGroupsToHide else ArrayList()
        getOptions()
    }

    fun getOptions() {

        disposable.add(view.searchSource()
                .debounce(500, TimeUnit.MILLISECONDS, schedulerProvider.io())
                .map { it.toString() }
                .map { textToSearch ->
                    var optionRepository = d2.optionModule().options()
                            .byOptionSetUid().eq(optionSetUid)

                    val finalOptionsToHide = ArrayList<String>()
                    val finalOptionsToShow = ArrayList<String>()

                    if (!optionsToHide.isNullOrEmpty())
                        finalOptionsToHide.addAll(optionsToHide!!)

                    if (!optionGroupsToShow.isNullOrEmpty()) {
                        for (groupUid in optionGroupsToShow!!) {
                            finalOptionsToShow.addAll(
                                    UidsHelper.getUidsList<ObjectWithUid>(
                                            d2.optionModule().optionGroups().withOptions().uid(groupUid).blockingGet()!!.options()!!)
                            )
                        }
                    }

                    if (!optionGroupsToHide.isNullOrEmpty()) {
                        for (groupUid in optionGroupsToHide!!) {
                            finalOptionsToHide.addAll(
                                    UidsHelper.getUidsList<ObjectWithUid>(
                                            d2.optionModule().optionGroups().withOptions().uid(groupUid).blockingGet()!!.options()!!)
                            )
                        }
                    }

                    if (finalOptionsToShow.isNotEmpty())
                        optionRepository = optionRepository.byUid().`in`(finalOptionsToShow)

                    if (finalOptionsToHide.isNotEmpty())
                        optionRepository = optionRepository.byUid().notIn(finalOptionsToHide)

                    if (textToSearch.isNotEmpty())
                        optionRepository = optionRepository.byDisplayName().like("%$textToSearch%")

                    optionRepository.getPaged(20)
                }
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe(
                        { view.setLiveData(it) },
                        { Timber.e(it) }
                ))
    }

    fun getCount(optionSetUid: String): Int? {
        return d2.optionModule().options().byOptionSetUid().eq(optionSetUid).blockingCount()
    }

    fun onDetach() {
        disposable.clear()
    }
}

