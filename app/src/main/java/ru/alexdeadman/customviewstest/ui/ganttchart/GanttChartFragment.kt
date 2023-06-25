package ru.alexdeadman.customviewstest.ui.ganttchart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.alexdeadman.customviewstest.databinding.FragmentGanttChartBinding
import ru.alexdeadman.customviewstest.ui.ganttchart.GanttView.Task
import java.time.LocalDate

class GanttChartFragment : Fragment() {

    private var _binding: FragmentGanttChartBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGanttChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val now = LocalDate.now()

        binding.ganttView.tasks = listOf(
            Task(
                name = "Task 1",
                dateStart = now.minusMonths(1),
                dateEnd = now
            ),
            Task(
                name = "Task 2 long name",
                dateStart = now.minusWeeks(2),
                dateEnd = now.plusWeeks(1)
            ),
            Task(
                name = "Task 3",
                dateStart = now.minusMonths(2),
                dateEnd = now.plusMonths(2)
            ),
            Task(
                name = "Some Task 4",
                dateStart = now.plusWeeks(2),
                dateEnd = now.plusMonths(2).plusWeeks(1)
            ),
            Task(
                name = "Task 5",
                dateStart = now.minusMonths(2).minusWeeks(1),
                dateEnd = now.plusWeeks(1)
            ),
            Task(
                name = "Task 6",
                dateStart = now.minusMonths(3),
                dateEnd = now.minusMonths(2)
            ),
            Task(
                name = "Task 7",
                dateStart = now.plusMonths(3),
                dateEnd = now.plusMonths(4)
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
